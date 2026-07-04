package org.togetherjava.tjbot.features.purge;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.moderation.audit.ModAuditLogWriter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Slash command that bulk-deletes messages in a text channel posted after a given anchor message.
 * <p>
 * The anchor message itself is preserved (deletion starts exclusively from messages newer than it).
 * An optional {@code amount} option caps the number of messages deleted; when omitted, the command
 * keeps deleting until no newer messages remain.
 * <p>
 * Because this command is destructive, it presents an ephemeral confirmation dialog before any
 * deletion runs, and rejects anchors older than {@link #MAX_ANCHOR_AGE} to avoid accidentally
 * purging large swathes of channel history. Completed purges are written to the moderation audit
 * log.
 */
public class PurgeCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PurgeCommand.class);
    private static final String CHANNEL_OPTION = "channel";
    private static final String MESSAGE_OPTION = "message-id";
    private static final String MINUTES_OPTION = "minutes";
    private static final String AMOUNT_OPTION = "amount";
    private static final Duration MAX_ANCHOR_AGE = Duration.ofDays(2);

    private final ModAuditLogWriter modAuditLogWriter;

    /**
     * Constructs the command and registers its options ({@code channel}, optional
     * {@code message-id}, optional {@code minutes}, optional {@code amount}).
     *
     * @param modAuditLogWriter used to record completed purges for moderator review
     */
    public PurgeCommand(ModAuditLogWriter modAuditLogWriter) {
        super("purge", "Deletes all messages in a channel after the given message id",
                CommandVisibility.GUILD);

        this.modAuditLogWriter = modAuditLogWriter;

        getData()
            .addOptions(
                    new OptionData(OptionType.CHANNEL, CHANNEL_OPTION, "The channel to purge", true)
                        .setChannelTypes(ChannelType.TEXT))
            .addOptions(new OptionData(OptionType.STRING, MESSAGE_OPTION,
                    "The message id to start purging from (exclusive)", false))
            .addOptions(new OptionData(OptionType.INTEGER, MINUTES_OPTION,
                    "Purge messages sent in the last N minutes (alternative to message-id)", false)
                .setMinValue(1)
                .setMaxValue(MAX_ANCHOR_AGE.toMinutes()))
            .addOptions(new OptionData(OptionType.INTEGER, AMOUNT_OPTION,
                    "The amount of messages to delete (default: all)", false)
                .setMinValue(1));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        TextChannel channel = Objects.requireNonNull(event.getOption(CHANNEL_OPTION))
            .getAsChannel()
            .asTextChannel();

        OptionMapping messageOption = event.getOption(MESSAGE_OPTION);
        OptionMapping minutesOption = event.getOption(MINUTES_OPTION);

        if ((messageOption == null) == (minutesOption == null)) {
            event.reply("Provide exactly one of `message-id` or `minutes`.")
                .setEphemeral(true)
                .queue();
            return;
        }

        ResolvedAnchor anchor = messageOption != null ? resolveMessageAnchor(event, messageOption)
                : resolveMinutesAnchor(minutesOption);

        OptionMapping amountOption = event.getOption(AMOUNT_OPTION);
        int amount = amountOption == null ? Integer.MAX_VALUE : amountOption.getAsInt();
        String amountLabel = amount == Integer.MAX_VALUE ? "all" : Integer.toString(amount);

        String description = "About to delete up to **%s** messages from %s, %s.".formatted(
                amountLabel, channel.getAsMention(), Objects.requireNonNull(anchor).description());

        String confirmId = generateComponentId(PurgeHelper.CONFIRM_ACTION, channel.getId(),
                anchor.snowflake(), Integer.toString(amount));
        String cancelId = generateComponentId(PurgeHelper.CANCEL_ACTION);

        PurgeHelper.sendConfirmationDialog(event, "Confirm purge", description, confirmId,
                cancelId);
    }

    private @Nullable ResolvedAnchor resolveMessageAnchor(SlashCommandInteractionEvent event,
            OptionMapping messageOption) {
        String messageId = messageOption.getAsString();
        long anchorIdLong;
        try {
            anchorIdLong = Long.parseLong(messageId);
        } catch (NumberFormatException _) {
            event.reply("The provided message id is not a valid snowflake.")
                .setEphemeral(true)
                .queue();
            return null;
        }

        Instant anchorCreatedAt = TimeUtil.getTimeCreated(anchorIdLong).toInstant();
        Duration anchorAge = Duration.between(anchorCreatedAt, Instant.now());
        if (anchorAge.compareTo(MAX_ANCHOR_AGE) > 0) {
            event.reply(
                    "Refusing to purge: anchor message is older than %d days. Pick a more recent anchor."
                        .formatted(MAX_ANCHOR_AGE.toDays()))
                .setEphemeral(true)
                .queue();
            return null;
        }

        return new ResolvedAnchor(messageId, "starting after message `%s` (sent <t:%d:R>)"
            .formatted(messageId, anchorCreatedAt.getEpochSecond()));
    }

    private ResolvedAnchor resolveMinutesAnchor(OptionMapping minutesOption) {
        int minutes = minutesOption.getAsInt();
        Instant anchorCreatedAt = Instant.now().minus(Duration.ofMinutes(minutes));
        String snowflake =
                Long.toUnsignedString(TimeUtil.getDiscordTimestamp(anchorCreatedAt.toEpochMilli()));
        return new ResolvedAnchor(snowflake,
                "sent in the last **%d** minute%s".formatted(minutes, minutes == 1 ? "" : "s"));
    }

    private record ResolvedAnchor(String snowflake, String description) {
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        String action = args.getFirst();

        if (PurgeHelper.CANCEL_ACTION.equals(action)) {
            PurgeHelper.handleCancel(event);
            return;
        }

        if (!PurgeHelper.CONFIRM_ACTION.equals(action)) {
            return;
        }

        String channelId = args.get(1);
        String messageId = args.get(2);
        int amount = Integer.parseInt(args.get(3));

        TextChannel channel =
                Objects.requireNonNull(event.getGuild()).getTextChannelById(channelId);
        if (channel == null) {
            event.editMessage("That channel no longer exists.").setEmbeds().setComponents().queue();
            return;
        }

        event.editMessage("Purging... this may take a while.").setEmbeds().setComponents().queue();

        logger.info("Purge initiated by {} in channel {} starting from messageId {} (amount: {})",
                event.getUser().getId(), channel.getName(), messageId, amount);

        PurgeHelper.purgeChannelMessages(channel, messageId, amount, 0, _ -> true, total -> {
            event.getHook()
                .editOriginal("Purge complete: deleted %d messages from %s.".formatted(total,
                        channel.getAsMention()))
                .queue();

            modAuditLogWriter.write("/purge",
                    "Deleted %d messages from %s".formatted(total, channel.getAsMention()),
                    event.getUser(), Instant.now(), Objects.requireNonNull(event.getGuild()));
        });
    }
}
