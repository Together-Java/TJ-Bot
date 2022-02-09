package org.togetherjava.tjbot.commands.reminder;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This creates a command called {@code /remind}, which reminds the user about a certain activity or
 * event
 *
 * <p>
 * For example:
 *
 * <pre>
 * {@code
 * /remind from: 26.09.2021 to: 03.10.2021 @User Message
 * /remind 10 seconds @User User Message
 * }
 * </pre>
 */

public class ReminderCommand extends SlashCommandAdapter {

    static final Color AMBIENT_COLOR = Color.decode("#FA8072");
    private static final String REMINDER = "reminder";
    private static final String OPTIONAL_USER_MENTION = "remind-who";
    private static final String OPTIONAL_USER_MESSAGE = "user-message";

    /*
     * Creates an Instance of the command.
     */
    public ReminderCommand() {
        super("reminder", "Reminds user about certain activity or event",
                SlashCommandVisibility.GUILD);
        getData()
            .addOption(OptionType.STRING, REMINDER,
                    "Reminds the user about certain activity or an event", true)
            .addOption(OptionType.USER, OPTIONAL_USER_MENTION, "Optionally the user to be reminded",
                    false)
            .addOption(OptionType.STRING, OPTIONAL_USER_MESSAGE, "Optionally to write a message",
                    false);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String reminder = Objects.requireNonNull(event.getOption(REMINDER)).getAsString();
        OptionMapping userMention = event.getOption(OPTIONAL_USER_MENTION);
        OptionMapping userMessage = event.getOption(OPTIONAL_USER_MESSAGE);

        String[] data = reminder.split(" ", 2);
        // TODO Make it so that it can support multiple units like: 1 minute 20 seconds, 2 hour 20
        // minutes, 2 days 20 hours
        int duration = Integer.parseInt(data[0]);
        ChronoUnit unit = switch (data[1]) {
            case "second", "seconds" -> ChronoUnit.SECONDS;
            case "minute", "minutes" -> ChronoUnit.MINUTES;
            case "hour", "hours" -> ChronoUnit.HOURS;
            case "day", "days" -> ChronoUnit.DAYS;
            default -> throw new IllegalArgumentException(
                    "Unsupported duration: " + reminder + " use Numbers only (EN)");
        };
        ReplyAction option;
        if (userMessage != null && userMention != null) {
            option = setMentionAndMessageReply(event, reminder, userMention, userMessage, duration,
                    unit);
        } else if (userMention != null) {
            option = setUserMentionReply(event, reminder, userMention, duration, unit);
        } else if (userMessage != null) {
            option = setUserMessageReply(event, reminder, userMessage, duration, unit);
        } else {
            option = event.replyEmbeds(new EmbedBuilder().setTitle("Reminder")
                .setDescription("You will be reminded in " + reminder)
                .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
                .setTimestamp(Instant.now())
                .setColor(AMBIENT_COLOR)
                .build());
            event.getChannel()
                .sendMessage(event.getUser().getAsMention() + " pinging you just to remind you.")
                .queueAfter(duration, TimeUnit.of(unit));
        }
        option.queue();
    }

    @NotNull
    private ReplyAction setMentionAndMessageReply(@NotNull SlashCommandEvent event, String reminder,
            OptionMapping userMention, OptionMapping userMessage, int duration, ChronoUnit unit) {
        ReplyAction option;
        option = event
            .replyEmbeds(new EmbedBuilder().setTitle("Reminder")
                .setDescription("User " + userMention.getAsUser().getAsMention()
                        + " will be reminded in " + reminder + " with the message **"
                        + userMessage.getAsString() + "**")
                .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
                .setTimestamp(Instant.now())
                .setColor(AMBIENT_COLOR)
                .build());
        event.getChannel()
            .sendMessage("Reminder for " + userMention.getAsUser().getAsMention() + " about **"
                    + userMessage.getAsString() + "**.")
            .queueAfter(duration, TimeUnit.of(unit));
        return option;
    }

    @NotNull
    private ReplyAction setUserMentionReply(@NotNull SlashCommandEvent event, String reminder,
            OptionMapping userMention, int duration, ChronoUnit unit) {
        ReplyAction option;
        option = event.replyEmbeds(new EmbedBuilder().setTitle("Reminder")
            .setDescription("User " + userMention.getAsUser().getAsMention()
                    + " will be reminded in " + reminder)
            .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
            .setTimestamp(Instant.now())
            .setColor(AMBIENT_COLOR)
            .build());

        event.getChannel()
            .sendMessage(
                    userMention.getAsUser().getAsMention() + " pinging you just to remind you.")
            .queueAfter(duration, TimeUnit.of(unit));
        return option;
    }

    @NotNull
    private ReplyAction setUserMessageReply(@NotNull SlashCommandEvent event, String reminder,
            OptionMapping userMessage, int duration, ChronoUnit unit) {
        ReplyAction option;
        option = event.replyEmbeds(new EmbedBuilder().setTitle("Reminder")
            .setDescription(
                    reminder + " reminder with the message **" + userMessage.getAsString() + "**")
            .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
            .setTimestamp(Instant.now())
            .setColor(AMBIENT_COLOR)
            .build());
        event.getChannel()
            .sendMessage(event.getUser().getAsMention() + " reminder with the message **"
                    + userMessage.getAsString() + "**")
            .queueAfter(duration, TimeUnit.of(unit));
        return option;
    }
}
