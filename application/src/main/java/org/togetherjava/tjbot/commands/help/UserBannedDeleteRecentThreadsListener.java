package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jooq.Record1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.db.Database;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.togetherjava.tjbot.db.generated.Tables.HELP_THREADS;

/**
 * Reacts on users being banned from a guild. It then deletes (not just closes) all their recently
 * created help threads.
 * <p>
 * This for example tackles threads created by scammers, which are otherwise not automatically
 * cleaned up on ban.
 */
public final class UserBannedDeleteRecentThreadsListener extends ListenerAdapter
        implements EventReceiver {
    private static final Logger logger =
            LoggerFactory.getLogger(UserBannedDeleteRecentThreadsListener.class);
    private static final Duration RECENT_THREAD_DURATION = Duration.ofMinutes(30);

    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database to find help threads created by the banned user
     */
    public UserBannedDeleteRecentThreadsListener(Database database) {
        this.database = database;
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        getUsersRecentHelpThreadIDs(event.getUser()).stream()
            .map(event.getJDA()::getThreadChannelById)
            .filter(Objects::nonNull)
            .forEach(threadChannel -> threadChannel.delete().queue(any -> {
            }, failure -> logger.warn("Failed to delete thread {} from banned user {}.",
                    threadChannel.getId(), event.getUser().getId(), failure)));
    }

    private List<Long> getUsersRecentHelpThreadIDs(User user) {
        Instant recentThreadThreshold = Instant.now().minus(RECENT_THREAD_DURATION);

        return database
            .read(context -> context.select(HELP_THREADS.CHANNEL_ID)
                .from(HELP_THREADS)
                .where(HELP_THREADS.AUTHOR_ID.eq(user.getIdLong())
                    .and(HELP_THREADS.CREATED_AT.greaterThan(recentThreadThreshold)))
                .fetch())
            .stream()
            .map(Record1::value1)
            .toList();
    }
}
