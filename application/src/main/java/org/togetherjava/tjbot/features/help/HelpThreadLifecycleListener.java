package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.EventReceiver;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.togetherjava.tjbot.db.generated.tables.HelpThreads.HELP_THREADS;

/**
 * Listens for help thread events after creation of thread. Updates metadata based on those events
 * in database.
 */
public final class HelpThreadLifecycleListener extends ListenerAdapter implements EventReceiver {
    private static final Logger logger = LoggerFactory.getLogger(HelpThreadLifecycleListener.class);
    private final HelpSystemHelper helper;
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
    public void onChannelUpdateArchived(ChannelUpdateArchivedEvent event) {
        ThreadChannel threadChannel = event.getChannel().asThreadChannel();

        if (!helper.isHelpForumName(threadChannel.getParentChannel().getName())) {
            return;
        }
        handleThreadStatus(threadChannel);
    }

    @Override
    public void onChannelUpdateAppliedTags(ChannelUpdateAppliedTagsEvent event) {
        ThreadChannel threadChannel = event.getChannel().asThreadChannel();

        if (!helper.isHelpForumName(threadChannel.getParentChannel().getName())
                || shouldIgnoreUpdatedTagEvent(event)) {
            return;
        }


        String newlyAppliedTagsOnly = event.getNewTags()
            .stream()
            .filter(helper::shouldIgnoreTag)
            .map(ForumTag::getName)
            .collect(Collectors.joining(","));


        long threadId = threadChannel.getIdLong();

        handleTagsUpdate(threadId, newlyAppliedTagsOnly);
    }

    private void handleThreadStatus(ThreadChannel threadChannel) {
        Instant closedAt = threadChannel.getTimeArchiveInfoLastModified().toInstant();
        long threadId = threadChannel.getIdLong();
        boolean isArchived = threadChannel.isArchived();

        if (isArchived) {
            handleArchiveStatus(closedAt, threadId, threadChannel.getJDA());
            return;
        }

        updateThreadStatusToActive(threadId);
    }

    void handleArchiveStatus(Instant closedAt, long id, JDA jda) {
        ThreadChannel threadChannel = jda.getThreadChannelById(id);
        if (threadChannel == null) {
            logger.info("thread with id: {} no longer exists, marking archived in records", id);
            database.write(context -> context.update(HELP_THREADS)
                .set(HELP_THREADS.CLOSED_AT, closedAt)
                .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ARCHIVED.val)
                .where(HELP_THREADS.CHANNEL_ID.eq(id))
                .execute());
            return;
        }

        long threadId = threadChannel.getIdLong();
        int messageCount = threadChannel.getMessageCount();
        int participantsExceptAuthor = threadChannel.getMemberCount() - 1;

        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.CLOSED_AT, closedAt)
            .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ARCHIVED.val)
            .set(HELP_THREADS.MESSAGE_COUNT, messageCount)
            .set(HELP_THREADS.PARTICIPANTS, participantsExceptAuthor)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Thread with id: {}, updated to archived status in database", threadId);

    }

    private void updateThreadStatusToActive(long threadId) {
        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ACTIVE.val)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Thread with id: {}, updated to active status in database", threadId);
    }

    private void handleTagsUpdate(long threadId, String updatedTag) {
        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.TAGS, updatedTag)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Updated tag for thread with id: {} in database", threadId);
    }

    /**
     * will ignore updated tag event if all new tags belong to the categories config
     * 
     * @param event updated tags event
     * @return boolean
     */
    private boolean shouldIgnoreUpdatedTagEvent(ChannelUpdateAppliedTagsEvent event) {
        List<ForumTag> newTags =
                event.getNewTags().stream().filter(helper::shouldIgnoreTag).toList();
        return newTags.isEmpty();
    }
}
