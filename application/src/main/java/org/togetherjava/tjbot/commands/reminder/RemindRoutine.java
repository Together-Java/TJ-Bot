package org.togetherjava.tjbot.commands.reminder;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
 * <p>
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
                sendReminder(jda, pendingReminder.getId(), pendingReminder.getGuildId(),
                        pendingReminder.getChannelId(), pendingReminder.getAuthorId(),
                        pendingReminder.getContent(), pendingReminder.getCreatedAt());
                pendingReminder.delete();
            }));
    }

    private static void sendReminder(@NotNull JDA jda, long id, long guildId, long channelId,
            long authorId, @NotNull CharSequence content, @NotNull TemporalAccessor createdAt) {
        RestAction<ReminderRoute> route = computeReminderRoute(jda, guildId, channelId, authorId);
        sendReminderViaRoute(route, id, content, createdAt);
    }

    private static RestAction<ReminderRoute> computeReminderRoute(@NotNull JDA jda, long guildId,
            long channelId, long authorId) {
        // If guild and channel can still be found, send there
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel != null) {
                return createGuildReminderRoute(jda, authorId, channel);
            }
        }

        // Otherwise, attempt to DM the user directly
        return createDmReminderRoute(jda, authorId);
    }

    private static @NotNull RestAction<ReminderRoute> createGuildReminderRoute(@NotNull JDA jda,
            long authorId, @NotNull TextChannel channel) {
        return jda.retrieveUserById(authorId)
            .onErrorMap(error -> null)
            .map(author -> new ReminderRoute(channel, author,
                    author == null ? null : author.getAsMention()));
    }

    private static @NotNull RestAction<ReminderRoute> createDmReminderRoute(@NotNull JDA jda,
            long authorId) {
        return jda.openPrivateChannelById(authorId)
            .onErrorMap(error -> null)
            .map(channel -> channel == null ? new ReminderRoute(null, null, null)
                    : new ReminderRoute(channel, channel.getUser(),
                            "(Sending your reminder directly, because I was unable to"
                                    + " locate the original channel you wanted it to be send to)"));
    }

    private static void sendReminderViaRoute(@NotNull RestAction<ReminderRoute> routeAction,
            long id, @NotNull CharSequence content, @NotNull TemporalAccessor createdAt) {
        routeAction.flatMap(route -> {
            if (route.isUndeliverable()) {
                throw new IllegalStateException("Route is not deliverable");
            }

            MessageEmbed embed = createReminderEmbed(content, createdAt, route.target());
            if (route.description() == null) {
                return route.channel().sendMessageEmbeds(embed);
            }
            return route.channel().sendMessage(route.description()).setEmbeds(embed);
        }).queue(message -> {
        }, failure -> logger.warn(
                "Failed to send a reminder (id '{}'), skipping it. This can be due to a network issue,"
                        + " but also happen if the bot disconnected from the target guild and the"
                        + " user has disabled DMs or has been deleted.",
                id));
    }

    private static @NotNull MessageEmbed createReminderEmbed(@NotNull CharSequence content,
            @NotNull TemporalAccessor createdAt, @Nullable User author) {
        String authorName = author == null ? "Unknown user" : author.getAsTag();
        String authorIconUrl = author == null ? null : author.getAvatarUrl();

        return new EmbedBuilder().setAuthor(authorName, null, authorIconUrl)
            .setDescription(content)
            .setFooter("reminder from")
            .setTimestamp(createdAt)
            .setColor(AMBIENT_COLOR)
            .build();
    }

    private record ReminderRoute(@Nullable MessageChannel channel, @Nullable User target,
            @Nullable String description) {
        boolean isUndeliverable() {
            return channel == null && target == null;
        }
    }
}
