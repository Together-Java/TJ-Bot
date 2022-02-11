package org.togetherjava.tjbot.commands.reminder;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.db.Database;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.db.generated.Tables.PENDING_REMINDERS;

/**
 * Routine that processes and sends pending reminders.
 *
 * Reminders can be set by using {@link RemindCommand}.
 */
public final class RemindRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(RemindRoutine.class);
    private static final Color AMBIENT_COLOR = Color.decode("#F7F492");
    private static final int SCHEDULE_INTERVAL_SECONDS = 30;
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database the database that contains the pending reminders to send.
     */
    public RemindRoutine(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, SCHEDULE_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        Instant now = Instant.now();
        database.write(context -> context.selectFrom(PENDING_REMINDERS)
            .where(PENDING_REMINDERS.REMIND_AT.lessOrEqual(now))
            .stream()
            .forEach(pendingReminder -> {
                sendReminder(jda, pendingReminder.getGuildId(), pendingReminder.getChannelId(),
                        pendingReminder.getAuthorId(), pendingReminder.getContent(),
                        pendingReminder.getCreatedAt());
                pendingReminder.delete();
            }));
    }

    private static void sendReminder(@NotNull JDA jda, long guildId, long channelId, long authorId,
            @NotNull CharSequence content, @NotNull TemporalAccessor createdAt) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.debug(
                    "Attempted to send a reminder but the bot is not connected to the guild '{}' anymore, skipping reminder.",
                    guildId);
            return;
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            logger.debug(
                    "Attempted to send a reminder but the guild '{}' does not have a channel with id '{}' anymore, skipping reminder.",
                    guildId, channelId);
            return;
        }

        jda.retrieveUserById(authorId).map(author -> {
            String authorName = author == null ? "Unknown user" : author.getAsTag();
            String authorIconUrl = author == null ? null : author.getAvatarUrl();

            return new EmbedBuilder().setAuthor(authorName, null, authorIconUrl)
                .setTitle("Reminder")
                .setDescription(content)
                .setFooter("from")
                .setTimestamp(createdAt)
                .setColor(AMBIENT_COLOR)
                .build();
        }).flatMap(channel::sendMessageEmbeds).queue();
    }
}
