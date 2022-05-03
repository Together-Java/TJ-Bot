package org.togetherjava.tjbot.commands.reminder;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;

import java.time.*;
import java.time.temporal.TemporalAmount;
import java.util.List;

import static org.togetherjava.tjbot.db.generated.Tables.PENDING_REMINDERS;

/**
 * Implements the '/remind' command which can be used to automatically send reminders to oneself at
 * a future date.
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * /remind time-amount: 5 time-unit: weeks content: Hello World!
 * }
 * </pre>
 * <p>
 * Pending reminders are processed and send by {@link RemindRoutine}.
 */
public final class RemindCommand extends SlashCommandAdapter {
    private static final String COMMAND_NAME = "remind";
    static final String TIME_AMOUNT_OPTION = "time-amount";
    static final String TIME_UNIT_OPTION = "time-unit";
    static final String CONTENT_OPTION = "content";

    private static final int MIN_TIME_AMOUNT = 1;
    private static final int MAX_TIME_AMOUNT = 1_000;
    private static final List<String> TIME_UNITS =
            List.of("minutes", "hours", "days", "weeks", "months", "years");
    private static final Period MAX_TIME_PERIOD = Period.ofYears(3);
    static final int MAX_PENDING_REMINDERS_PER_USER = 100;

    private final Database database;

    /**
     * Creates an instance of the command.
     *
     * @param database to store and fetch the reminders from
     */
    public RemindCommand(@NotNull Database database) {
        super(COMMAND_NAME, "Reminds you after a given time period has passed (e.g. in 5 weeks)",
                SlashCommandVisibility.GUILD);

        // TODO As soon as JDA offers date/time selector input, this should also offer
        // "/remind at" next to "/remind in" and use subcommands then
        OptionData timeAmount = new OptionData(OptionType.INTEGER, TIME_AMOUNT_OPTION,
                "period to remind you in, the amount of time (e.g. [5] weeks)", true)
                    .setRequiredRange(MIN_TIME_AMOUNT, MAX_TIME_AMOUNT);
        OptionData timeUnit = new OptionData(OptionType.STRING, TIME_UNIT_OPTION,
                "period to remind you in, the unit of time (e.g. 5 [weeks])", true);
        TIME_UNITS.forEach(unit -> timeUnit.addChoice(unit, unit));

        getData().addOptions(timeUnit, timeAmount)
            .addOption(OptionType.STRING, CONTENT_OPTION, "what to remind you about", true);

        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        int timeAmount = Math.toIntExact(event.getOption(TIME_AMOUNT_OPTION).getAsLong());
        String timeUnit = event.getOption(TIME_UNIT_OPTION).getAsString();
        String content = event.getOption(CONTENT_OPTION).getAsString();

        Instant remindAt = parseWhen(timeAmount, timeUnit);
        User author = event.getUser();
        Guild guild = event.getGuild();

        if (!handleIsRemindAtWithinLimits(remindAt, event)) {
            return;
        }
        if (!handleIsUserBelowMaxPendingReminders(author, guild, event)) {
            return;
        }

        event.reply("Will remind you about '%s' in %d %s.".formatted(content, timeAmount, timeUnit))
            .setEphemeral(true)
            .queue();

        database.write(context -> context.newRecord(PENDING_REMINDERS)
            .setCreatedAt(Instant.now())
            .setGuildId(guild.getIdLong())
            .setChannelId(event.getChannel().getIdLong())
            .setAuthorId(author.getIdLong())
            .setRemindAt(remindAt)
            .setContent(content)
            .insert());
    }

    private static @NotNull Instant parseWhen(int whenAmount, @NotNull String whenUnit) {
        TemporalAmount period = switch (whenUnit) {
            case "second", "seconds" -> Duration.ofSeconds(whenAmount);
            case "minute", "minutes" -> Duration.ofMinutes(whenAmount);
            case "hour", "hours" -> Duration.ofHours(whenAmount);
            case "day", "days" -> Period.ofDays(whenAmount);
            case "week", "weeks" -> Period.ofWeeks(whenAmount);
            case "month", "months" -> Period.ofMonths(whenAmount);
            case "year", "years" -> Period.ofYears(whenAmount);
            default -> throw new IllegalArgumentException("Unsupported unit, was: " + whenUnit);
        };

        return ZonedDateTime.now(ZoneOffset.UTC).plus(period).toInstant();
    }

    private static boolean handleIsRemindAtWithinLimits(@NotNull Instant remindAt,
            @NotNull IReplyCallback event) {
        ZonedDateTime maxWhen = ZonedDateTime.now(ZoneOffset.UTC).plus(MAX_TIME_PERIOD);

        if (remindAt.atZone(ZoneOffset.UTC).isBefore(maxWhen)) {
            return true;
        }

        event
            .reply("The reminder is set too far in the future. The maximal allowed period is '%s'."
                .formatted(MAX_TIME_PERIOD))
            .setEphemeral(true)
            .queue();

        return false;
    }

    private boolean handleIsUserBelowMaxPendingReminders(@NotNull ISnowflake author,
            @NotNull ISnowflake guild, @NotNull IReplyCallback event) {
        int pendingReminders = database.read(context -> context.fetchCount(PENDING_REMINDERS,
                PENDING_REMINDERS.AUTHOR_ID.equal(author.getIdLong())
                    .and(PENDING_REMINDERS.GUILD_ID.equal(guild.getIdLong()))));

        if (pendingReminders < MAX_PENDING_REMINDERS_PER_USER) {
            return true;
        }

        event.reply(
                "You have reached the maximum amount of pending reminders per user (%s). Please wait until some of them have been sent."
                    .formatted(MAX_PENDING_REMINDERS_PER_USER))
            .setEphemeral(true)
            .queue();

        return false;
    }
}
