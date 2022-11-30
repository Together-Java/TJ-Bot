package org.togetherjava.tjbot.commands.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;

import org.togetherjava.tjbot.commands.EventReceiver;

import javax.annotation.Nonnull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Listens for new help threads being created. That is, a user posted a question in the help forum.
 * <p>
 * Will for example record thread metadata in the database and send an explanation message to the
 * user.
 */
public final class HelpThreadCreatedListener extends ListenerAdapter implements EventReceiver {
    private final HelpSystemHelper helper;
    private final Cache<Long, Instant> threadIdToCreatedAtCache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(2, TimeUnit.of(ChronoUnit.MINUTES))
        .build();

    /**
     * Creates a new instance.
     *
     * @param helper to work with the help threads
     */
    public HelpThreadCreatedListener(HelpSystemHelper helper) {
        this.helper = helper;
    }

    @Override
    public void onChannelCreate(@Nonnull ChannelCreateEvent createEvent) {
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

        createMessages(threadChannel).queue();
    }

    private RestAction<Message> createMessages(ThreadChannel threadChannel) {
        return sendHelperHeadsUp(threadChannel).flatMap(Message::pin)
            .flatMap(any -> helper.sendExplanationMessage(threadChannel));
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
}
