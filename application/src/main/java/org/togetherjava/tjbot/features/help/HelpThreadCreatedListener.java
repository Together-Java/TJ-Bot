package org.togetherjava.tjbot.features.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;

import org.togetherjava.tjbot.features.EventReceiver;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private static final int FIRST_TEN = 10;
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
    public void onChannelCreate(ChannelCreateEvent createEvent) {
        if (!createEvent.getChannelType().isThread()) {
            return;
        }
        ThreadChannel threadChannel = createEvent.getChannel().asThreadChannel();

        if (wasThreadAlreadyHandled(threadChannel.getIdLong())) {
            return;
        }

        if (!helper.isHelpForumName(threadChannel.getParentChannel().getName())) {
            return;
        }
        handleHelpThreadCreated(threadChannel);
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
        helper.writeHelpThreadToDatabase(threadChannel.getOwnerIdLong(), threadChannel);

        // The creation is delayed, because otherwise it could be too fast and be executed
        // after Discord created the thread, but before Discord send OPs initial message.
        // Sending messages at that moment is not allowed.
        createMessages(threadChannel).and(pinOriginalQuestion(threadChannel))
            .queueAfter(5, TimeUnit.SECONDS);

        deletePinnedAnnouncement(threadChannel);
    }

    private RestAction<Message> createAIResponse(ThreadChannel threadChannel) {
        RestAction<Message> originalQuestion =
                threadChannel.retrieveMessageById(threadChannel.getIdLong());
        return originalQuestion.flatMap(message -> helper.constructChatGptAttempt(threadChannel,
                getMessageContent(message), componentIdInteractor));
    }

    private RestAction<Void> pinOriginalQuestion(ThreadChannel threadChannel) {
        return threadChannel.retrieveMessageById(threadChannel.getIdLong()).flatMap(Message::pin);
    }

    private RestAction<Message> createMessages(ThreadChannel threadChannel) {
        return sendHelperHeadsUp(threadChannel)
            .flatMap(any -> helper.sendExplanationMessage(threadChannel))
            .flatMap(any -> createAIResponse(threadChannel));
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
        ThreadChannel channel = event.getChannel().asThreadChannel();
        Member interactionUser = Objects.requireNonNull(event.getMember());
        if (channel.getOwnerIdLong() != interactionUser.getIdLong()
                && !helper.hasTagManageRole(interactionUser)) {
            event.reply("You do not have permission for this action.").setEphemeral(true).queue();
            return;
        }

        RestAction<Void> deleteMessages = event.getMessage().delete();
        for (String id : args) {
            deleteMessages = deleteMessages.and(channel.deleteMessageById(id));
        }
        deleteMessages.queue();
    }

    @Override
    public void onSelectMenuSelection(SelectMenuInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    private void deletePinnedAnnouncement(ThreadChannel channel) {
        channel.getHistoryFromBeginning(FIRST_TEN)
            .map(historyFromBeginning -> historyFromBeginning.getRetrievedHistory().stream())
            .map(historyStream -> historyStream
                .filter(message -> message.getType() == MessageType.CHANNEL_PINNED_ADD)
                .findFirst())
            .map(Optional::get)
            .flatMap(Message::delete)
            .queueAfter(10, TimeUnit.SECONDS);
    }

}
