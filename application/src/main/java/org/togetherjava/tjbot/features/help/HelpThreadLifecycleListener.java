package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.EventReceiver;

import java.time.Instant;

import static org.togetherjava.tjbot.db.generated.tables.HelpThreads.HELP_THREADS;

/**
 * Listens for help thread events after creation of thread. Updates metadata based on those events
 * in database.
 */
public final class HelpThreadLifecycleListener extends ListenerAdapter implements EventReceiver {

    private final HelpSystemHelper helper;
    private final Logger logger = LoggerFactory.getLogger(HelpThreadLifecycleListener.class);
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param helper to work with the help threads
     * @param database the database to store help thread metadata in
     */
    public HelpThreadLifecycleListener(HelpSystemHelper helper, Database database) {
        this.helper = helper;
        this.database = database;
    }

    @Override
    public void onChannelUpdateArchived(@NotNull ChannelUpdateArchivedEvent event) {
        if (!event.getChannelType().isThread()) {
            return;
        }
        ThreadChannel threadChannel = event.getChannel().asThreadChannel();

        if (!helper.isHelpForumName(threadChannel.getParentChannel().getName())) {
            return;
        }
        handleThreadStatus(threadChannel);
    }

    @Override
    public void onChannelUpdateAppliedTags(@NotNull ChannelUpdateAppliedTagsEvent event) {
        ThreadChannel threadChannel = event.getChannel().asThreadChannel();

        if (!helper.isHelpForumName(threadChannel.getParentChannel().getName())) {
            return;
        }

        String updatedTag = event.getAddedTags().getFirst().getName();
        long threadId = threadChannel.getIdLong();

        handleTagsUpdate(threadId, updatedTag);
    }

    private void handleThreadStatus(ThreadChannel threadChannel) {
        Instant closedAt = Instant.now();
        long threadId = threadChannel.getIdLong();

        int status = database.read(context -> context.selectFrom(HELP_THREADS)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .fetchOne(HELP_THREADS.TICKET_STATUS));

        if (status == HelpSystemHelper.TicketStatus.ACTIVE.val) {
            handleArchiveStatus(closedAt, threadChannel);
            return;
        }

        changeStatusToActive(threadId);
    }

    private void handleArchiveStatus(Instant closedAt, ThreadChannel threadChannel) {
        long threadId = threadChannel.getIdLong();
        int messageCount = threadChannel.getMessageCount();
        int participantsExceptAuthor = threadChannel.getMemberCount() - 1;

        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.CLOSED_AT, closedAt)
            .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ARCHIVED.val)
                .set(HELP_THREADS.MESSAGE_COUNT,messageCount)
                .set(HELP_THREADS.PARTICIPANTS,participantsExceptAuthor)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Thread with id: {}, updated to archived status in database", threadId);
    }

    private void changeStatusToActive(long threadId) {
        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ACTIVE.val)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Thread with id: {}, updated to active status in database", threadId);
    }

    private void handleTagsUpdate(long threadId, String updatedTag) {
        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.TAG, updatedTag)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Updated tag for thread with id: {} in database", threadId);
    }
}
