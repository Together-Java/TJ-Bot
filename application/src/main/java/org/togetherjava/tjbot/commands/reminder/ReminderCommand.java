package org.togetherjava.tjbot.commands.reminder;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jooq.Result;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.PendingRemindersRecord;

import java.time.*;
import java.time.temporal.TemporalAmount;
import java.util.List;

import static org.togetherjava.tjbot.db.generated.Tables.PENDING_REMINDERS;

/**
 * Implements the '/reminder' command which can be used to automatically send reminders to oneself
 * at a future date.
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * /reminder create time-amount: 5 time-unit: weeks content: Hello World!
 * }
 *
 * {@code
 * /reminder list
 * }
 * </pre>
 * <p>
 * Pending reminders are processed and send by {@link RemindRoutine}.
 */
public final class ReminderCommand extends SlashCommandAdapter {
    private static final String COMMAND_NAME = "reminder";
    private static final String LIST_SUBCOMMAND = "list";
    static final String CREATE_SUBCOMMAND = "create";
    static final String TIME_AMOUNT_OPTION = "time-amount";
    static final String TIME_UNIT_OPTION = "time-unit";
    static final String CONTENT_OPTION = "content";

    private static final int MIN_TIME_AMOUNT = 1;
    private static final int MAX_TIME_AMOUNT = 1_000;
    private static final List<String> TIME_UNITS =
            List.of("minutes", "hours", "days", "weeks", "months", "years");
    private static final Period MAX_TIME_PERIOD = Period.ofYears(3);
    private static final int REMINDERS_PER_PAGE = 10;
    private static final int MAX_REMINDER_TITLE_LENGTH = 256;
    private static final String PREVIOUS_BUTTON_LABEL = "⬅";
    private static final String NEXT_BUTTON_LABEL = "➡";
    static final int MAX_PENDING_REMINDERS_PER_USER = 100;

    private final Database database;

    /**
     * Creates an instance of the command.
     *
     * @param database to store and fetch the reminders from
     */
    public ReminderCommand(Database database) {
        super(COMMAND_NAME, "Reminds you after a given time period has passed (e.g. in 5 weeks)",
                CommandVisibility.GUILD);

        // TODO As soon as JDA offers date/time selector input, this should also offer
        // "/remind at" next to "/remind in" and use subcommands then
        OptionData timeAmount = new OptionData(OptionType.INTEGER, TIME_AMOUNT_OPTION,
                "period to remind you in, the amount of time (e.g. [5] weeks)", true)
                    .setRequiredRange(MIN_TIME_AMOUNT, MAX_TIME_AMOUNT);
        OptionData timeUnit = new OptionData(OptionType.STRING, TIME_UNIT_OPTION,
                "period to remind you in, the unit of time (e.g. 5 [weeks])", true);
        TIME_UNITS.forEach(unit -> timeUnit.addChoice(unit, unit));
        OptionData content =
                new OptionData(OptionType.STRING, CONTENT_OPTION, "what to remind you about", true);

        getData().addSubcommands(
                new SubcommandData(CREATE_SUBCOMMAND, "creates a reminder").addOptions(timeAmount,
                        timeUnit, content),
                new SubcommandData(LIST_SUBCOMMAND, "shows all your currently pending reminders"));

        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case CREATE_SUBCOMMAND -> handleCreateCommand(event);
            case LIST_SUBCOMMAND -> handleListCommand(event);
            default -> throw new AssertionError(
                    "Unexpected Subcommand: " + event.getSubcommandName());
        }
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        int pageNumber = Integer.parseInt(args.get(0));
        int pageTurnBy = Integer.parseInt(args.get(1));

        Result<PendingRemindersRecord> pendingReminders =
                getPendingReminders(event.getGuild(), event.getUser());
        int pageToShow = pageNumber + pageTurnBy;

        event
            .editMessage(
                    MessageEditData.fromCreateData(createListMessage(pendingReminders, pageToShow)))
            .queue();
    }

    private void handleCreateCommand(SlashCommandInteractionEvent event) {
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

    private void handleListCommand(SlashCommandInteractionEvent event) {
        Result<PendingRemindersRecord> pendingReminders =
                getPendingReminders(event.getGuild(), event.getUser());

        event.reply(createListMessage(pendingReminders, 1)).setEphemeral(true).queue();
    }

    private Result<PendingRemindersRecord> getPendingReminders(Guild guild, User user) {
        return database.read(context -> context.selectFrom(PENDING_REMINDERS)
            .where(PENDING_REMINDERS.GUILD_ID.eq(guild.getIdLong())
                .and(PENDING_REMINDERS.AUTHOR_ID.eq(user.getIdLong())))
            .orderBy(PENDING_REMINDERS.REMIND_AT.asc())
            .fetch());
    }

    private MessageCreateData createListMessage(Result<PendingRemindersRecord> pendingReminders,
            int pageToShow) {
        int totalPages = Math.ceilDiv(pendingReminders.size(), REMINDERS_PER_PAGE);

        EmbedBuilder remindersEmbed = new EmbedBuilder().setTitle("Pending reminders")
            .setColor(RemindRoutine.AMBIENT_COLOR);
        MessageCreateBuilder listMessage = new MessageCreateBuilder();

        if (pendingReminders.isEmpty()) {
            remindersEmbed.setDescription("No pending reminders");
        } else {
            if (totalPages > 1) {
                remindersEmbed.setFooter("Page: %d/%d".formatted(pageToShow, totalPages));

                getPageEntries(pendingReminders, pageToShow)
                    .forEach(reminder -> addReminderAsField(reminder, remindersEmbed));

                listMessage.addActionRow(createPageTurnButtons(pageToShow, totalPages));
            } else {
                pendingReminders.forEach(reminder -> addReminderAsField(reminder, remindersEmbed));
            }
        }

        return listMessage.addEmbeds(remindersEmbed.build()).build();
    }

    private List<Button> createPageTurnButtons(int pageNumber, int totalPages) {
        int previousButtonTurnPageBy = -1;
        Button previousButton =
                createPageTurnButton(PREVIOUS_BUTTON_LABEL, pageNumber, previousButtonTurnPageBy);
        if (pageNumber <= 1) {
            previousButton = previousButton.asDisabled();
        }

        int nextButtonTurnPageBy = 1;
        Button nextButton =
                createPageTurnButton(NEXT_BUTTON_LABEL, pageNumber, nextButtonTurnPageBy);
        if (pageNumber >= totalPages) {
            nextButton = nextButton.asDisabled();
        }

        return List.of(previousButton, nextButton);
    }

    private Button createPageTurnButton(String label, long pageNumber, int turnPageBy) {
        return Button.primary(
                generateComponentId(String.valueOf(pageNumber), String.valueOf(turnPageBy)), label);
    }

    private static List<PendingRemindersRecord> getPageEntries(
            Result<PendingRemindersRecord> remindersRecords, int pageNumber) {
        int start = (pageNumber - 1) * REMINDERS_PER_PAGE;
        int end = Math.min(start + REMINDERS_PER_PAGE, remindersRecords.size());

        return remindersRecords.subList(start, end);
    }

    private static void addReminderAsField(PendingRemindersRecord reminder, EmbedBuilder embed) {
        String content = reminder.getContent();
        long channelId = reminder.getChannelId();
        Instant remindAt = reminder.getRemindAt();

        String description = """
                Channel: %s
                Remind at: %s""".formatted(MessageUtils.mentionChannelById(channelId),
                TimeFormat.DEFAULT.format(remindAt));

        embed.addField(MessageUtils.abbreviate(content, MAX_REMINDER_TITLE_LENGTH), description,
                false);
    }

    private static Instant parseWhen(int whenAmount, String whenUnit) {
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

    private static boolean handleIsRemindAtWithinLimits(Instant remindAt, IReplyCallback event) {
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

    private boolean handleIsUserBelowMaxPendingReminders(ISnowflake author, ISnowflake guild,
            IReplyCallback event) {
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
