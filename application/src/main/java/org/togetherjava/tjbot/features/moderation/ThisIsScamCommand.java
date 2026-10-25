package org.togetherjava.tjbot.features.moderation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;
import org.togetherjava.tjbot.features.utils.AmbientColors;
import org.togetherjava.tjbot.features.utils.Guilds;
import org.togetherjava.tjbot.features.utils.MessageUtils;
import org.togetherjava.tjbot.logging.LogMarkers;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Allows users to alert moderators about a message that violates the rules. Moderators then decide
 * how to handle it:
 * <ul>
 * <li><b>Scam</b>: the author is quarantined, then banned and unbanned to delete their recent
 * messages.</li>
 * <li><b>Job-Ad</b>: the author is warned, then banned and unbanned to delete their recent
 * messages.</li>
 * <li><b>Dismiss</b>: nothing happens, the alert is closed.</li>
 * </ul>
 * The ban and unban only serves to delete the messages, the user is free to join back afterward.
 */
public final class ThisIsScamCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final Logger logger = LoggerFactory.getLogger(ThisIsScamCommand.class);

    private static final String COMMAND_NAME = "this-is-against-rules";

    private static final String SCAM_REASON = "Message was reported and confirmed as scam";
    private static final String JOB_AD_REASON =
            "Message was reported and confirmed as a job advertisement";

    private static final String FAILED_MESSAGE =
            "Sorry, there was an issue forwarding your report to the moderators. We are investigating.";

    private static final Duration USER_COMMAND_COOLDOWN = Duration.ofMinutes(1);

    private final Config config;
    private final ModerationActionsStore actionsStore;
    private final Predicate<String> isModMailChannel;

    private final Cache<Long, Instant> reportedMessageToTimestamp =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofDays(1)).build();
    private final Cache<Long, Instant> userToLastCommandUse = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(USER_COMMAND_COOLDOWN)
        .build();

    /**
     * Creates a new instance.
     *
     * @param config resolve the mod channel
     * @param actionsStore used to store issued moderative actions
     */
    public ThisIsScamCommand(Config config, ModerationActionsStore actionsStore) {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);

        this.config = Objects.requireNonNull(config);
        this.actionsStore = Objects.requireNonNull(actionsStore);
        isModMailChannel = Pattern.compile(config.getModMailChannelPattern()).asMatchPredicate();
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        if (handleIsOnCooldown(event)) {
            return;
        }
        if (handleWasAlreadyReportedMessage(event)) {
            return;
        }

        Message message = event.getTarget();
        Optional<TextChannel> modChannel = findModChannel(event);
        if (modChannel.isEmpty()) {
            reportedMessageToTimestamp.invalidate(message.getIdLong());
            event.reply(FAILED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        reportToMods(message, modChannel.orElseThrow()).mapToResult().map(result -> {
            if (result.isFailure()) {
                reportedMessageToTimestamp.invalidate(message.getIdLong());
                logger.warn("Unable to forward a report to the mod mail channel.",
                        result.getFailure());
                return FAILED_MESSAGE;
            }
            return "Thank you for your report, a moderator will take care of it 👍";
        }).flatMap(response -> event.reply(response).setEphemeral(true)).queue();
    }

    private boolean handleIsOnCooldown(MessageContextInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        Instant lastCommandUse = userToLastCommandUse.getIfPresent(userId);
        Runnable isNotOnCooldownAction = () -> userToLastCommandUse.put(userId, Instant.now());

        if (lastCommandUse == null) {
            isNotOnCooldownAction.run();
            return false;
        }
        Instant momentCooldownEnds = lastCommandUse.plus(USER_COMMAND_COOLDOWN);
        if (Instant.now().isAfter(momentCooldownEnds)) {
            isNotOnCooldownAction.run();
            return false;
        }

        event.reply("You just reported a message, please wait a bit.").setEphemeral(true).queue();
        return true;
    }

    private boolean handleWasAlreadyReportedMessage(MessageContextInteractionEvent event) {
        long messageId = event.getTarget().getIdLong();
        if (reportedMessageToTimestamp.getIfPresent(messageId) != null) {
            event.reply("This message was already reported.").setEphemeral(true).queue();
            return true;
        }

        reportedMessageToTimestamp.put(messageId, Instant.now());
        return false;
    }

    private Optional<TextChannel> findModChannel(MessageContextInteractionEvent event) {
        Guild guild = Objects.requireNonNull(event.getGuild());
        Optional<TextChannel> modMailChannel = Guilds.findTextChannel(guild, isModMailChannel);
        if (modMailChannel.isEmpty()) {
            logger.warn(
                    "Cannot find the designated mod mail channel in guild '{}' with the pattern '{}'",
                    guild.getId(), config.getModMailChannelPattern());
        }
        return modMailChannel;
    }

    private MessageCreateAction reportToMods(Message message, TextChannel modMailChannel) {
        User author = message.getAuthor();
        String description = createDescription(message);
        long accountAgeDays = ChronoUnit.DAYS.between(author.getTimeCreated(),
                OffsetDateTime.now(ZoneOffset.UTC));

        MessageEmbed reportEmbed = new EmbedBuilder().setTitle("Rule Violation")
            .setDescription(
                    MessageUtils.abbreviate(description, MessageEmbed.DESCRIPTION_MAX_LENGTH))
            .setAuthor(author.getName(), null, author.getEffectiveAvatarUrl())
            .setTimestamp(message.getTimeCreated())
            .setColor(AmbientColors.MODERATION_SCAM)
            .setFooter("%s - account age %d days".formatted(author.getId(), accountAgeDays))
            .build();

        String guildId = String.valueOf(message.getGuild().getIdLong());
        String authorId = String.valueOf(author.getIdLong());

        return modMailChannel.sendMessageEmbeds(reportEmbed)
            .addActionRow(
                    Button.success(generateComponentId(guildId, authorId, ReportAction.SCAM.name()),
                            "Scam"),
                    Button.success(
                            generateComponentId(guildId, authorId, ReportAction.JOB_AD.name()),
                            "Job-Ad"),
                    Button.danger(
                            generateComponentId(guildId, authorId, ReportAction.DISMISS.name()),
                            "Dismiss"));
    }

    private static String createDescription(Message target) {
        String content = target.getContentStripped();
        String description = content.isBlank() ? "(message had no text content)" : content;

        String attachmentText = "_none_";
        List<Message.Attachment> attachments = target.getAttachments();
        if (!attachments.isEmpty()) {
            attachmentText = attachments.stream()
                .map(Message.Attachment::getFileName)
                .collect(Collectors.joining("\n- ", "\n- ", ""));
        }

        description += """


                Attachments: %s
                [Go to message](%s)
                """.formatted(attachmentText, target.getJumpUrl());
        return description;
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        long guildId = Long.parseLong(args.get(0));
        long targetId = Long.parseLong(args.get(1));
        ReportAction action = ReportAction.valueOf(args.get(2));

        Member moderator = Objects.requireNonNull(event.getMember());
        List<MessageEmbed> existingEmbeds = new ArrayList<>(event.getMessage().getEmbeds());

        if (action == ReportAction.DISMISS) {
            event
                .editMessageEmbeds(createResponseEmbeds(existingEmbeds,
                        "Dismissed, no action executed.", Color.RED))
                .setComponents()
                .queue();
            return;
        }

        ErrorHandler errorHandler = new ErrorHandler()
            .handle(ErrorResponse.UNKNOWN_USER, failure -> logger.debug(LogMarkers.SENSITIVE,
                    "Attempted to handle user-reported scam, but user '{}' does not exist anymore.",
                    targetId))
            .handle(ErrorResponse.UNKNOWN_MEMBER, failure -> logger.debug(LogMarkers.SENSITIVE,
                    "Attempted to handle user-reported scam, but user '{}' is not a member of guild '{}' anymore.",
                    targetId, guildId));

        Guild guild = Objects.requireNonNull(event.getJDA().getGuildById(guildId));

        guild.retrieveMemberById(targetId)
            .queue(target -> handleConfirmedViolation(event, action, guild, moderator, target,
                    existingEmbeds), errorHandler);
    }

    private void handleConfirmedViolation(ButtonInteractionEvent event, ReportAction action,
            Guild guild, Member moderator, Member target, List<MessageEmbed> alertEmbeds) {

        Member bot = guild.getSelfMember();

        boolean canExecute = switch (action) {
            case SCAM -> {
                Role quarantinedRole =
                        ModerationUtils.getQuarantinedRole(guild, config).orElse(null);
                yield ModerationUtils.handleRoleChangeChecks(quarantinedRole, "quarantine", target,
                        bot, moderator, guild, SCAM_REASON, event);
            }
            case JOB_AD ->
                ModerationUtils.handleCanInteractWithTarget("ban", bot, moderator, target, event);
            default -> false;
        };

        canExecute &= ModerationUtils.handleHasBotPermissions("ban", Permission.BAN_MEMBERS, bot,
                guild, event);

        if (!canExecute) {
            logger.warn(LogMarkers.SENSITIVE,
                    "Moderator '{}' tried to execute report for user '{}' in guild '{}', but checks failed.",
                    moderator.getId(), target.getId(), guild.getId());
            return;
        }

        String successText;
        RestAction<Void> handling = switch (action) {
            case SCAM -> {
                successText = "This is scam. The user was quarantined and messages were deleted.";
                yield quarantineAndRemove(guild, target, moderator);
            }
            case JOB_AD -> {
                successText = "This is a job-ad. The user was warned and messages were deleted.";
                yield warnAndRemove(guild, target, moderator);
            }
            default -> throw new IllegalStateException("Unknown action provided: " + action);
        };

        String pendingText = "%s is handling this alert...".formatted(moderator.getAsMention());

        event.editMessageEmbeds(createResponseEmbeds(alertEmbeds, pendingText, Color.ORANGE))
            .setComponents()
            .queue(hook -> {
                Consumer<Void> onSuccess =
                        ignored -> hook
                            .editOriginalEmbeds(createResponseEmbeds(alertEmbeds,
                                    "%s (handled by %s)".formatted(successText,
                                            moderator.getAsMention()),
                                    Color.GREEN))
                            .queue();

                Consumer<Throwable> onFailure = failure -> {
                    logger.warn(LogMarkers.SENSITIVE,
                            "Failed to handle alert for user '{}' in guild '{}'.", target.getId(),
                            guild.getId(), failure);
                    hook.editOriginalEmbeds(createResponseEmbeds(alertEmbeds,
                            "Handling this alert failed, please check the logs and handle the user manually.",
                            Color.RED));
                };

                handling.queue(onSuccess, onFailure);
            });
    }

    private RestAction<Void> quarantineAndRemove(Guild guild, Member target, Member moderator) {
        String description =
                """
                        Hey there, sorry to tell you but unfortunately you have been put under quarantine after sending scam.
                        This means you can no longer interact with anyone in the server until you have been unquarantined again.

                        Potentially your account got compromised.
                        After you regained control of your account make sure you secure it properly, for example by using 2FA.
                        You can then join back our server and contact the mods to get unquarantined.""";

        return sendDm(guild, target.getUser(), "Quarantine", description, SCAM_REASON)
            .flatMap(_ -> {
                recordAction(guild, target, moderator, ModerationAction.QUARANTINE, SCAM_REASON);
                return guild
                    .addRoleToMember(target,
                            ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
                    .reason(SCAM_REASON);
            })
            .flatMap(_ -> deleteMessagesByBanThenUnban(guild, target.getUser(), SCAM_REASON));
    }

    private RestAction<Void> warnAndRemove(Guild guild, Member target, Member moderator) {
        String description =
                """
                        Hey there, sorry to tell you but unfortunately you have been warned in the server for posting a job advertisement.
                        To delete your messages you were briefly removed from the server, you are welcome to join back.""";

        return sendDm(guild, target.getUser(), "Warning", description, JOB_AD_REASON).flatMap(_ -> {
            recordAction(guild, target, moderator, ModerationAction.WARN, JOB_AD_REASON);
            return deleteMessagesByBanThenUnban(guild, target.getUser(), JOB_AD_REASON);
        }).flatMap(_ -> deleteMessagesByBanThenUnban(guild, target.getUser(), JOB_AD_REASON));
    }

    private static RestAction<Boolean> sendDm(Guild guild, User target, String title,
            String description, String reason) {
        return ModerationUtils.sendModActionDm(
                ModerationUtils.getModActionEmbed(guild, title, description, reason, true), target);
    }

    private void recordAction(Guild guild, Member target, Member moderator, ModerationAction action,
            String reason) {
        logger.info(LogMarkers.SENSITIVE,
                "'{}' ({}) executed '{}' against the user '{}' ({}) in guild '{}' for reason '{}'.",
                moderator.getUser().getName(), moderator.getId(), action,
                target.getUser().getName(), target.getId(), guild.getName(), reason);

        actionsStore.addAction(guild.getIdLong(), moderator.getIdLong(), target.getIdLong(), action,
                null, reason);
    }

    private static RestAction<Void> deleteMessagesByBanThenUnban(Guild guild, User target,
            String reason) {
        return guild.ban(target, 1, TimeUnit.DAYS)
            .reason(reason)
            .flatMap(_ -> guild.unban(target).reason(reason));
    }

    private static List<MessageEmbed> createResponseEmbeds(List<MessageEmbed> stagedEmbeds,
            String text, Color color) {
        MessageEmbed resultEmbed = new EmbedBuilder().setDescription(text).setColor(color).build();

        List<MessageEmbed> embeds = new ArrayList<>(stagedEmbeds);
        embeds.add(resultEmbed);

        return embeds;
    }

    private enum ReportAction {
        SCAM,
        JOB_AD,
        DISMISS
    }
}
