package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.EventReceiver;

import java.time.Instant;

import static org.togetherjava.tjbot.db.generated.tables.HelpThreads.HELP_THREADS;

public final class HelpThreadArchivedListener extends ListenerAdapter implements EventReceiver {

    private final HelpSystemHelper helper;
    private final Logger logger = LoggerFactory.getLogger(HelpThreadArchivedListener.class);
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param helper to work with the help threads
     */
    public HelpThreadArchivedListener(HelpSystemHelper helper, Database database) {
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

    private void handleThreadStatus(ThreadChannel threadChannel) {
        long threadId = threadChannel.getIdLong();
        Instant closedAt = Instant.now();

        int status = database.read(context -> context.selectFrom(HELP_THREADS)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .fetchOne(HELP_THREADS.TICKET_STATUS));

        if (status == HelpSystemHelper.TicketStatus.ACTIVE.val) {
            changeStatusToArchive(closedAt, threadId);
        }

        changeStatusToActive(threadId);
    }

    private void changeStatusToArchive(Instant closedAt, long threadId) {
        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.CLOSED_AT, closedAt)
            .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ARCHIVED.val)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Thread with id: {}, updated to archived in database", threadId);
    }

    private void changeStatusToActive(long threadId) {
        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ACTIVE.val)
            .where(HELP_THREADS.CHANNEL_ID.eq(threadId))
            .execute());

        logger.info("Thread with id: {}, updated to active status in database", threadId);
    }
}
