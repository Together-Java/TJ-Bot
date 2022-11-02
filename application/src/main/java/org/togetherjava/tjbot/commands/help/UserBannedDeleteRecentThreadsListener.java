package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jooq.Record1;

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
        getRecentHelpThreads(event.getUser()).stream()
            .map(event.getJDA()::getThreadChannelById)
            .filter(Objects::nonNull)
            .map(ThreadChannel::delete)
            .forEach(restAction -> restAction.queue(onSuccess -> {
            }, onFailure -> {

            }));
    }

    private List<Long> getRecentHelpThreads(User user) {
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
