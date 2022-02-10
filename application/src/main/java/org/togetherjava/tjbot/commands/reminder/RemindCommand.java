package org.togetherjava.tjbot.commands.reminder;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
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

// TODO Javadoc
public final class RemindCommand extends SlashCommandAdapter {
    private static final String COMMAND_NAME = "remind";
    private static final String WHEN_AMOUNT_OPTION = "when-amount";
    private static final String WHEN_UNIT_OPTION = "when-unit";
    private static final String CONTENT_OPTION = "content";

    private static final int MIN_WHEN_AMOUNT = 1;
    private static final int MAX_WHEN_AMOUNT = 1_000;
    private static final List<String> WHEN_UNITS =
            List.of("minutes", "hours", "days", "weeks", "months", "years");
    private static final Period MAX_WHEN_PERIOD = Period.ofYears(3);

    private final Database database;

    /**
     * Creates an instance of the command.
     *
     * @param database to store and fetch the reminders from
     */
    public RemindCommand(@NotNull Database database) {
        super(COMMAND_NAME, "Reminds the user about something at a given time",
                SlashCommandVisibility.GUILD);

        OptionData whenAmount = new OptionData(OptionType.INTEGER, WHEN_AMOUNT_OPTION,
                "when the reminder should be sent, amount (e.g. 5)", true)
                    .setRequiredRange(MIN_WHEN_AMOUNT, MAX_WHEN_AMOUNT);
        OptionData whenUnit = new OptionData(OptionType.STRING, WHEN_UNIT_OPTION,
                "when the reminder should be sent, unit (e.g. weeks)", true);
        WHEN_UNITS.forEach(unit -> whenUnit.addChoice(unit, unit));

        getData().addOptions(whenAmount, whenUnit)
            .addOption(OptionType.STRING, CONTENT_OPTION, "the content of the reminder", true);

        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        int whenAmount = Math.toIntExact(event.getOption(WHEN_AMOUNT_OPTION).getAsLong());
        String whenUnit = event.getOption(WHEN_UNIT_OPTION).getAsString();
        String content = event.getOption(CONTENT_OPTION).getAsString();

        Instant when = parseWhen(whenAmount, whenUnit);
        if (!handleIsWhenWithinLimits(when, event)) {
            return;
        }

        event.reply("Will remind you about '%s' in %d %s.".formatted(content, whenAmount, whenUnit))
            .setEphemeral(true)
            .queue();

        database.write(context -> context.newRecord(PENDING_REMINDERS)
            .setCreatedAt(Instant.now())
            .setGuildId(event.getGuild().getIdLong())
            .setChannelId(event.getChannel().getIdLong())
            .setAuthorId(event.getUser().getIdLong())
            .setRemindAt(when)
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

    private static boolean handleIsWhenWithinLimits(@NotNull Instant when,
            @NotNull Interaction event) {
        ZonedDateTime maxWhen = ZonedDateTime.now(ZoneOffset.UTC).plus(MAX_WHEN_PERIOD);

        if (when.atZone(ZoneOffset.UTC).isBefore(maxWhen)) {
            return true;
        }

        event
            .reply("The reminder is set too far in the future. The maximal allowed period is '%s'."
                .formatted(MAX_WHEN_PERIOD))
            .setEphemeral(true)
            .queue();

        return false;
    }
}
