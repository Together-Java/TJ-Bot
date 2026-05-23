package org.togetherjava.tjbot.features.purge;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.moderation.audit.ModAuditLogWriter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Slash command that deletes messages authored by a given user across every channel they have view
 * access to within a recent time window.
 * <p>
 * Because Discord exposes no "messages by user" endpoint, this command iterates each candidate
 * channel and paginates its recent history, filtering by author. It is therefore noticeably slower
 * than the single-channel {@link PurgeCommand} and intentionally caps its lookback at
 * {@link #MAX_WINDOW}.
 * <p>
 * Like {@link PurgeCommand} it shows a confirmation dialog before running and records completed
 * runs in the moderation audit log. Active threads are scanned; archived threads are skipped.
 */
public class PurgeMessagesByUserCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PurgeMessagesByUserCommand.class);

    private static final String USER_OPTION = "user";
    private static final String MINUTES_OPTION = "minutes";
    private static final String AMOUNT_OPTION = "amount";

    private static final Duration MAX_WINDOW = Duration.ofHours(6);
    private static final int PROGRESS_EDIT_EVERY_N_CHANNELS = 10;

    private final ModAuditLogWriter modAuditLogWriter;

    /**
     * Constructs the command and registers its options ({@code user}, {@code minutes}, optional
     * {@code amount}).
     *
     * @param modAuditLogWriter used to record completed purges for moderator review
     */
    public PurgeMessagesByUserCommand(ModAuditLogWriter modAuditLogWriter) {
        super("purge-messages-by-user",
                "Deletes a user's recent messages across all channels they can access",
                CommandVisibility.GUILD);

        this.modAuditLogWriter = modAuditLogWriter;

        getData()
            .addOptions(new OptionData(OptionType.USER, USER_OPTION,
                    "The user whose messages to purge", true))
            .addOptions(new OptionData(OptionType.INTEGER, MINUTES_OPTION,
                    "Purge messages sent in the last N minutes", true)
                .setMinValue(1)
                .setMaxValue(MAX_WINDOW.toMinutes()))
            .addOptions(new OptionData(OptionType.INTEGER, AMOUNT_OPTION,
                    "Global cap on the number of messages to delete (default: all)", false)
                .setMinValue(1));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Guild guild = Objects.requireNonNull(event.getGuild());
        User targetUser = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsUser();
        int minutes = Objects.requireNonNull(event.getOption(MINUTES_OPTION)).getAsInt();
        OptionMapping amountOption = event.getOption(AMOUNT_OPTION);
        int amount = amountOption == null ? Integer.MAX_VALUE : amountOption.getAsInt();

        Member targetMember = guild.getMember(targetUser);
        if (targetMember == null) {
            event.reply("That user is not a member of this guild.").setEphemeral(true).queue();
            return;
        }

        List<GuildMessageChannel> candidates = collectCandidateChannels(guild, targetMember);
        if (candidates.isEmpty()) {
            event
                .reply("Found no channels where %s has access and the bot can manage messages."
                    .formatted(targetUser.getAsMention()))
                .setEphemeral(true)
                .queue();
            return;
        }

        Instant anchorCreatedAt = Instant.now().minus(Duration.ofMinutes(minutes));
        String anchorSnowflake =
                Long.toUnsignedString(TimeUtil.getDiscordTimestamp(anchorCreatedAt.toEpochMilli()));

        String amountLabel = amount == Integer.MAX_VALUE ? "all" : Integer.toString(amount);
        String description =
                "About to delete up to **%s** messages by %s sent in the last **%d** minute%s, across **%d** channel%s. This may take several minutes."
                    .formatted(amountLabel, targetUser.getAsMention(), minutes,
                            minutes == 1 ? "" : "s", candidates.size(),
                            candidates.size() == 1 ? "" : "s");

        String confirmId = generateComponentId(PurgeHelper.CONFIRM_ACTION, targetUser.getId(),
                anchorSnowflake, Integer.toString(amount));
        String cancelId = generateComponentId(PurgeHelper.CANCEL_ACTION);

        PurgeHelper.sendConfirmationDialog(event, "Confirm user purge", description, confirmId,
                cancelId);
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

        long targetUserId = Long.parseLong(args.get(1));
        String anchorSnowflake = args.get(2);
        int amount = Integer.parseInt(args.get(3));

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member targetMember = guild.getMemberById(targetUserId);
        if (targetMember == null) {
            event.editMessage("That user is no longer a member of this guild.")
                .setEmbeds()
                .setComponents()
                .queue();
            return;
        }

        List<GuildMessageChannel> candidates = collectCandidateChannels(guild, targetMember);
        if (candidates.isEmpty()) {
            event.editMessage("No accessible channels remain to scan.")
                .setEmbeds()
                .setComponents()
                .queue();
            return;
        }

        event
            .editMessage("Purging across %d channels... this may take a while."
                .formatted(candidates.size()))
            .setEmbeds()
            .setComponents()
            .queue();

        logger.info(
                "User-purge initiated by {} targeting user {} ({} channels, amount: {}, anchor: {})",
                event.getUser().getId(), targetUserId, candidates.size(), amount, anchorSnowflake);

        Map<String, Integer> perChannel = new LinkedHashMap<>();
        PurgeContext ctx =
                new PurgeContext(targetUserId, anchorSnowflake, perChannel, event, totalDeleted -> {
                    User targetUser = targetMember.getUser();
                    event.getHook()
                        .editOriginal(
                                "Purge complete: deleted %d message%s from %s across %d channel%s."
                                    .formatted(totalDeleted, totalDeleted == 1 ? "" : "s",
                                            targetUser.getAsMention(), perChannel.size(),
                                            perChannel.size() == 1 ? "" : "s"))
                        .queue();

                    modAuditLogWriter.write("/purge-messages-by-user",
                            buildAuditDescription(targetUser, totalDeleted, perChannel),
                            event.getUser(), Instant.now(), guild);
                });

        purgeAcrossChannels(ctx, candidates.iterator(), amount, 0, 0);
    }

    private record PurgeContext(long targetUserId, String anchorSnowflake,
            Map<String, Integer> perChannel, ButtonInteractionEvent event, PurgeResult onComplete) {
    }

    private List<GuildMessageChannel> collectCandidateChannels(Guild guild, Member target) {
        Member bot = guild.getSelfMember();
        List<GuildMessageChannel> result = new ArrayList<>();

        guild.getTextChannelCache().forEach(channel -> {
            if (target.hasAccess(channel)
                    && bot.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
                result.add(channel);
            }
        });
        guild.getNewsChannelCache().forEach(channel -> {
            if (target.hasAccess(channel)
                    && bot.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
                result.add(channel);
            }
        });
        guild.getThreadChannelCache().forEach(thread -> {
            if (thread.isArchived()) {
                return;
            }
            if (target.hasAccess(thread) && bot.hasPermission(thread, Permission.MESSAGE_MANAGE)) {
                result.add(thread);
            }
        });

        return result;
    }

    private void purgeAcrossChannels(PurgeContext ctx, Iterator<GuildMessageChannel> channels,
            int remaining, int totalDeleted, int channelsProcessed) {
        if (!channels.hasNext() || remaining <= 0) {
            ctx.onComplete().run(totalDeleted);
            return;
        }

        GuildMessageChannel channel = channels.next();
        long targetUserId = ctx.targetUserId();

        PurgeHelper.purgeChannelMessages(channel, ctx.anchorSnowflake(), remaining, 0,
                m -> m.getAuthor().getIdLong() == targetUserId, channelDeleted -> {
                    if (channelDeleted > 0) {
                        ctx.perChannel().put(channel.getName(), channelDeleted);
                    }

                    int newTotal = totalDeleted + channelDeleted;
                    int newRemaining = remaining - channelDeleted;
                    int newProcessed = channelsProcessed + 1;

                    if (newProcessed % PROGRESS_EDIT_EVERY_N_CHANNELS == 0) {
                        ctx.event()
                            .getHook()
                            .editOriginal(
                                    "Purging... %d messages deleted so far.".formatted(newTotal))
                            .queue();
                    }

                    purgeAcrossChannels(ctx, channels, newRemaining, newTotal, newProcessed);
                });
    }

    private static String buildAuditDescription(User target, int total,
            Map<String, Integer> perChannel) {
        StringBuilder sb = new StringBuilder();
        sb.append("Deleted ")
            .append(total)
            .append(" messages by ")
            .append(target.getAsMention())
            .append(" (`")
            .append(target.getId())
            .append("`).");

        if (!perChannel.isEmpty()) {
            sb.append("\nBy channel:");
            perChannel.forEach(
                    (name, count) -> sb.append("\n• #").append(name).append(": ").append(count));
        }
        return sb.toString();
    }
}
