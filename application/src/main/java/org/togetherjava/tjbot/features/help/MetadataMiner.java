package org.togetherjava.tjbot.features.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.EventReceiver;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.Tables.HELP_THREADS;

public class MetadataMiner extends ListenerAdapter implements EventReceiver {
    private final Database database;
    private final Predicate<String> isHelpForumName;
    private static final Logger logger = LoggerFactory.getLogger(MetadataMiner.class);
    private final Cache<Long, Instant> threadIdToCreatedAtCache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(2, TimeUnit.of(ChronoUnit.MINUTES))
        .build();


    public MetadataMiner(Database database, Config config) {
        this.database = database;
        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!event.getChannelType().isThread()) {
            return;
        }

        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        String rootName = threadChannel.getParentChannel().getName();

        if (isHelpForumName.test(rootName)) {
            handleHelpThreadCreated(event, threadChannel);
        }
    }

    @Override
    public void onChannelUpdateArchived(@NotNull ChannelUpdateArchivedEvent event) {
        if (!event.getChannelType().isThread()
                || !event.getChannel().asThreadChannel().isArchived()) {
            return;
        }

        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        String rootName = threadChannel.getParentChannel().getName();

        if (isHelpForumName.test(rootName)) {
            handleHelpThreadClosed(threadChannel);
        }
    }

    private void handleHelpThreadCreated(ChannelCreateEvent event, ThreadChannel threadChannel) {
        if (wasThreadAlreadyHandled(threadChannel.getIdLong())) {
            return;
        }

        String tag = threadChannel.getAppliedTags().get(0).getName();
        long channelId = threadChannel.getIdLong();
        Instant createdAt = Instant.now();

        //ACTUAL AUTHOR SHOULD BE REGISTERED IF TRANSFERRED
        long authorId = threadChannel.getOwnerIdLong();

        logger.warn("ticket tags applied: "+tag);

            database.write(context -> context.newRecord(HELP_THREADS)
                .setChannelId(channelId)
                .setCreatedAt(createdAt)
                .setAuthorId(authorId)
                .setTicketStatus(TicketStatus.ACTIVE.val)
                .setTag(tag)
                .insert());
    }

    private void handleHelpThreadClosed(ThreadChannel threadChannel) {
        long channelId = threadChannel.getIdLong();
        Instant closedAt = Instant.now();

        // TODO: if thread is active again should be handled accordingly
        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.CLOSED_AT, closedAt)
            .set(HELP_THREADS.TICKET_STATUS, TicketStatus.ARCHIVED.val)
            .where(HELP_THREADS.CHANNEL_ID.eq(channelId))
            .execute());
    }

    private boolean wasThreadAlreadyHandled(long threadChannelId) {
        // NOTE Discord/JDA fires this event twice per thread (bug?), we work around by remembering
        // the threads we already handled
        Instant now = Instant.now();
        // NOTE It is necessary to do the "check if exists, otherwise insert" atomic
        Instant createdAt = threadIdToCreatedAtCache.get(threadChannelId, any -> now);
        return createdAt != now;
    }

    enum TicketStatus {
        ARCHIVED(0),
        ACTIVE(1);

        private final int val;
        TicketStatus(int val) {
            this.val = val;
        }
    }
}
