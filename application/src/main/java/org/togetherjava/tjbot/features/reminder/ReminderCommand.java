package org.togetherjava.tjbot.features.reminder;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jooq.Result;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.PendingRemindersRecord;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.utils.MessageUtils;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    private static final String CANCEL_COMMAND = "cancel";
    private static final String CANCEL_REMINDER_OPTION = "reminder";
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
    private static final Emoji PREVIOUS_BUTTON_EMOJI = Emoji.fromUnicode("⬅");
    private static final Emoji NEXT_BUTTON_EMOJI = Emoji.fromUnicode("➡");
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
                new SubcommandData(CANCEL_COMMAND, "cancels a pending reminder").addOption(
                        OptionType.STRING, CANCEL_REMINDER_OPTION, "reminder to cancel", true,
                        true),
                new SubcommandData(LIST_SUBCOMMAND, "shows all your currently pending reminders"));

        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case CREATE_SUBCOMMAND -> handleCreateCommand(event);
            case CANCEL_COMMAND -> handleCancelCommand(event);
            case LIST_SUBCOMMAND -> handleListCommand(event);
            default ->
                throw new AssertionError("Unexpected Subcommand: " + event.getSubcommandName());
        }
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        int pageToShow = Integer.parseInt(args.getFirst());

        EmojiUnion emoji = event.getButton().getEmoji();
        if (PREVIOUS_BUTTON_EMOJI.equals(emoji)) {
            pageToShow--;
        } else if (NEXT_BUTTON_EMOJI.equals(emoji)) {
            pageToShow++;
        }

        Result<PendingRemindersRecord> pendingReminders =
                getPendingReminders(event.getGuild(), event.getUser());

        MessageCreateData message = createPendingRemindersPage(pendingReminders, pageToShow);
        event.editMessage(MessageEditData.fromCreateData(message)).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();

        if (!focusedOption.getName().equals(CANCEL_REMINDER_OPTION)) {
            throw new AssertionError("Unexpected option, was : " + focusedOption.getName());
        }

        List<String> pendingReminders = getPendingReminders(event.getGuild(), event.getUser())
            .map(PendingRemindersRecord::getContent);
        List<Command.Choice> choices = StringDistances
            .closeMatches(focusedOption.getValue(), pendingReminders, REMINDERS_PER_PAGE)
            .stream()
            .map(content -> new Command.Choice(content, content))
            .toList();

        event.replyChoices(choices).queue();
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

    private void handleCancelCommand(SlashCommandInteractionEvent event) {
        String content = event.getOption(CANCEL_REMINDER_OPTION).getAsString();

        database.write(context -> context.delete(PENDING_REMINDERS)
            .where(PENDING_REMINDERS.CONTENT.eq(content)
                .and(PENDING_REMINDERS.AUTHOR_ID.eq(event.getUser().getIdLong())))
            .execute());

        event.reply("Your reminder is canceled").setEphemeral(true).queue();
    }

    private void handleListCommand(SlashCommandInteractionEvent event) {
        Result<PendingRemindersRecord> pendingReminders =
                getPendingReminders(event.getGuild(), event.getUser());

        event.reply(createPendingRemindersPage(pendingReminders, 1)).setEphemeral(true).queue();
    }

    private Result<PendingRemindersRecord> getPendingReminders(Guild guild, User user) {
        return database.read(context -> context.selectFrom(PENDING_REMINDERS)
            .where(PENDING_REMINDERS.GUILD_ID.eq(guild.getIdLong())
                .and(PENDING_REMINDERS.AUTHOR_ID.eq(user.getIdLong())))
            .orderBy(PENDING_REMINDERS.REMIND_AT.asc())
            .fetch());
    }

    private MessageCreateData createPendingRemindersPage(
            List<PendingRemindersRecord> pendingReminders, int pageToShow) {
        // 12 reminders, 10 per page, ceil(12 / 10) = 2
        int totalPages = Math.ceilDiv(pendingReminders.size(), REMINDERS_PER_PAGE);

        pageToShow = Math.clamp(pageToShow, 1, totalPages);

        EmbedBuilder remindersEmbed = new EmbedBuilder().setTitle("Pending reminders")
            .setColor(RemindRoutine.AMBIENT_COLOR);
        MessageCreateBuilder pendingRemindersPage = new MessageCreateBuilder();

        if (pendingReminders.isEmpty()) {
            remindersEmbed.setDescription("No pending reminders");
        } else {
            if (totalPages > 1) {
                pendingReminders = getPageEntries(pendingReminders, pageToShow);
                remindersEmbed.setFooter("Page: %d/%d".formatted(pageToShow, totalPages));
                pendingRemindersPage.addActionRow(createPageTurnButtons(pageToShow, totalPages));
            }
            pendingReminders.forEach(reminder -> addReminderAsField(reminder, remindersEmbed));
        }

        return pendingRemindersPage.addEmbeds(remindersEmbed.build()).build();
    }

    private List<Button> createPageTurnButtons(int currentPage, int totalPages) {
        String pageNumberString = String.valueOf(currentPage);

        Button previousButton =
                Button.primary(generateComponentId(pageNumberString), PREVIOUS_BUTTON_EMOJI);
        if (currentPage <= 1) {
            previousButton = previousButton.asDisabled();
        }

        Button nextButton =
                Button.primary(generateComponentId(pageNumberString), NEXT_BUTTON_EMOJI);
        if (currentPage >= totalPages) {
            nextButton = nextButton.asDisabled();
        }

        return List.of(previousButton, nextButton);
    }

    private static List<PendingRemindersRecord> getPageEntries(
            List<PendingRemindersRecord> remindersRecords, int pageToDisplay) {
        int start = (pageToDisplay - 1) * REMINDERS_PER_PAGE;
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

        embed.addField(MessageUtils.abbreviate(content, MessageEmbed.TITLE_MAX_LENGTH), description,
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
