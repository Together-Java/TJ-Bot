package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jooq.Result;

import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.HelpThreadsRecord;

import java.time.Duration;
import java.time.Instant;

import static org.togetherjava.tjbot.db.generated.Tables.HELP_THREADS;

/**
 * Implements the functionality to delete recent help threads when the user gets banned.
 */
public final class OnGuildBanDeleteSpamThreadListener extends ListenerAdapter
        implements EventReceiver {
    private static final Duration recentThreadDuration = Duration.ofMinutes(30);

    private final Database database;

    /**
     * @param database the database to get help threads from
     */
    public OnGuildBanDeleteSpamThreadListener(Database database) {
        this.database = database;
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        getRecentHelpThreads(event.getUser()).forEach(helpThreadsRecord -> event.getJDA()
            .getThreadChannelById(helpThreadsRecord.getChannelId())
            .delete()
            .queue());
    }

    private Result<HelpThreadsRecord> getRecentHelpThreads(User user) {
        return database
            .read(context -> context.selectFrom(HELP_THREADS)
                .where(HELP_THREADS.AUTHOR_ID.eq(user.getIdLong())
                    .and(HELP_THREADS.CREATED_AT
                        .greaterOrEqual(Instant.now().minus(recentThreadDuration)))))
            .fetch();
    }
}
