package org.togetherjava.tjbot.features.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;

import org.togetherjava.tjbot.features.EventReceiver;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;
import org.togetherjava.tjbot.features.utils.LinkDetection;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Listens for new help threads being created. That is, a user posted a question in the help forum.
 * <p>
 * Will for example record thread metadata in the database and send an explanation message to the
 * user.
 */
public final class HelpThreadCreatedListener extends ListenerAdapter
        implements EventReceiver, UserInteractor {
    private final HelpSystemHelper helper;

    private final Cache<Long, Instant> threadIdToCreatedAtCache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(2, TimeUnit.of(ChronoUnit.MINUTES))
        .build();
    private final ComponentIdInteractor componentIdInteractor =
            new ComponentIdInteractor(getInteractionType(), getName());

    /**
     * Creates a new instance.
     *
     * @param helper to work with the help threads
     */
    public HelpThreadCreatedListener(HelpSystemHelper helper) {
        this.helper = helper;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromThread()) {
            ThreadChannel threadChannel = event.getChannel().asThreadChannel();
            Channel parentChannel = threadChannel.getParentChannel();
            if (helper.isHelpForumName(parentChannel.getName())) {
                int messageCount = threadChannel.getMessageCount();
                if (messageCount > 1 || wasThreadAlreadyHandled(threadChannel.getIdLong())) {
                    return;
                }
                handleHelpThreadCreated(threadChannel);
            }
        }
    }

    private boolean wasThreadAlreadyHandled(long threadChannelId) {
        // NOTE Discord/JDA fires this event twice per thread (bug?), we work around by remembering
        // the threads we already handled
        Instant now = Instant.now();
        // NOTE It is necessary to do the "check if exists, otherwise insert" atomic
        Instant createdAt = threadIdToCreatedAtCache.get(threadChannelId, any -> now);
        return createdAt != now;
    }

    private void handleHelpThreadCreated(ThreadChannel threadChannel) {
        threadChannel.retrieveStartMessage().flatMap(message -> {
            registerThreadDataInDB(message, threadChannel);
            return sendHelperHeadsUp(threadChannel)
                .flatMap(any -> HelpThreadCreatedListener.isContextSufficient(message),
                        any -> createAIResponse(threadChannel, message))
                .flatMap(any -> pinOriginalQuestion(message));
        }).queue();
    }

    private static User getMentionedAuthorByMessage(Message message) {
        return message.getMentions().getUsers().getFirst();
    }

    private static boolean isPostedBySelfUser(Message message) {
        return message.getJDA().getSelfUser().equals(message.getAuthor());
    }

    private RestAction<Message> createAIResponse(ThreadChannel threadChannel, Message message) {
        return helper.constructChatGptAttempt(threadChannel, getMessageContent(message),
                componentIdInteractor);
    }

    private static boolean isContextSufficient(Message message) {
        return !MessageUtils.containsAttachments(message)
                && !LinkDetection.containsLink(message.getContentRaw());
    }

    private RestAction<Void> pinOriginalQuestion(Message message) {
        return message.pin();
    }

    private RestAction<Message> sendHelperHeadsUp(ThreadChannel threadChannel) {
        String alternativeMention = "Helper";
        String helperMention = helper.getCategoryTagOfChannel(threadChannel)
            .map(ForumTag::getName)
            .flatMap(category -> helper.handleFindRoleForCategory(category,
                    threadChannel.getGuild()))
            .map(Role::getAsMention)
            .orElse(alternativeMention);

        // We want to invite all members of a role, but without hard-pinging them. However,
        // manually inviting them is cumbersome and can hit rate limits.
        // Instead, we abuse the fact that a role-ping through an edit will not hard-ping users,
        // but still invite them to a thread.
        String headsUpPattern = "%s please have a look, thanks.";
        String headsUpWithoutRole = headsUpPattern.formatted(alternativeMention);
        String headsUpWithRole = headsUpPattern.formatted(helperMention);

        return threadChannel.sendMessage(headsUpWithoutRole)
            .flatMap(message -> message.editMessage(headsUpWithRole));
    }

    private String getMessageContent(Message message) {
        if (message.getEmbeds().isEmpty()) {
            return message.getContentRaw();
        }

        return message.getEmbeds()
            .stream()
            .map(MessageEmbed::getDescription)
            .collect(Collectors.joining("\n"));
    }

    @Override
    public String getName() {
        return "chatpgt-answer";
    }

    @Override
    public UserInteractionType getInteractionType() {
        return UserInteractionType.OTHER;
    }

    @Override
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        // This method handles chatgpt's automatic response "dismiss" button
        event.deferEdit().queue();

        ThreadChannel channel = event.getChannel().asThreadChannel();
        Member interactionUser = Objects.requireNonNull(event.getMember());

        channel.retrieveStartMessage()
            .queue(forumPostMessage -> handleDismiss(interactionUser, channel, forumPostMessage,
                    event, args));

    }

    private boolean isPostAuthor(Member interactionUser, Message message) {
        if (message.getEmbeds().isEmpty()) {
            return false;
        }

        String embedAuthor = Objects
            .requireNonNull(message.getEmbeds().getFirst().getAuthor(),
                    "embed author for forum post is null")
            .getName();

        return embedAuthor.equals(interactionUser.getUser().getName());
    }

    private boolean isAuthorized(Member interactionUser, ThreadChannel channel,
            Message forumPostMessage) {
        return (channel.getOwnerIdLong() == interactionUser.getIdLong())
                || helper.hasTagManageRole(interactionUser)
                || isPostAuthor(interactionUser, forumPostMessage);
    }

    private void handleDismiss(Member interactionUser, ThreadChannel channel,
            Message forumPostMessage, ButtonInteractionEvent event, List<String> args) {
        boolean isAuthorized = isAuthorized(interactionUser, channel, forumPostMessage);
        if (!isAuthorized) {
            event.getHook()
                .sendMessage("You do not have permission for this action.")
                .setEphemeral(true)
                .queue();
            return;
        }

        RestAction<Void> deleteMessages = event.getMessage().delete();
        for (String id : args) {
            deleteMessages = deleteMessages.and(channel.deleteMessageById(id));
        }
        deleteMessages.queue();
    }

    private void registerThreadDataInDB(Message message, ThreadChannel threadChannel) {
        long authorId = threadChannel.getOwnerIdLong();

        if (isPostedBySelfUser(message)) {
            // When transfer-command is used
            authorId = getMentionedAuthorByMessage(message).getIdLong();
        }

        helper.writeHelpThreadToDatabase(authorId, threadChannel);
    }
}
