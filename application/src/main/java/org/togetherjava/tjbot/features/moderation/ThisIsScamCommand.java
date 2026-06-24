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
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
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
 * Allows users to report a message as potential scam. Moderators can confirm the report from the
 * audit log, causing the author to be quarantined plus message history getting deleted.
 */
public final class ThisIsScamCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final Logger logger = LoggerFactory.getLogger(ThisIsScamCommand.class);

    private static final String COMMAND_NAME = "this-is-scam";

    private static final String ACTION_TITLE = "Quarantine";
    private static final String ACTION_REASON = "Message was reported and confirmed as scam";

    private static final String FAILED_MESSAGE =
            "Sorry, there was an issue forwarding your scam report to the moderators. We are investigating.";
    private static final Duration USER_COMMAND_COOLDOWN = Duration.ofMinutes(1);

    private final Config config;
    private final ModerationActionsStore actionsStore;
    private final Predicate<String> isModAuditLogChannel;

    private final Cache<Long, Instant> reportedMessageToTimestamp =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofDays(1)).build();
    private final Cache<Long, Instant> userToLastCommandUse = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(USER_COMMAND_COOLDOWN)
        .build();

    /**
     * Creates a new instance.
     *
     * @param config to resolve the moderation audit log channel and quarantined role
     * @param actionsStore used to store issued quarantine actions
     */
    public ThisIsScamCommand(Config config, ModerationActionsStore actionsStore) {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);

        this.config = Objects.requireNonNull(config);
        this.actionsStore = Objects.requireNonNull(actionsStore);
        isModAuditLogChannel =
                Pattern.compile(config.getModAuditLogChannelPattern()).asMatchPredicate();
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        if (handleIsOnCooldown(event)) {
            return;
        }
        if (handleWasAlreadyReportedMessage(event)) {
            return;
        }

        Optional<TextChannel> modAuditLog = findModAuditLogChannel(event);
        if (modAuditLog.isEmpty()) {
            event.reply(FAILED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        Message message = event.getTarget();
        reportToMods(message, modAuditLog.orElseThrow()).mapToResult().map(result -> {
            if (result.isFailure()) {
                logger.warn("Unable to forward a scam report to the mod audit log channel.",
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

        event.reply("You just reported a message as scam, please wait a bit.")
            .setEphemeral(true)
            .queue();
        return true;
    }

    private boolean handleWasAlreadyReportedMessage(MessageContextInteractionEvent event) {
        long messageId = event.getTarget().getIdLong();
        if (reportedMessageToTimestamp.getIfPresent(messageId) != null) {
            event.reply("This message was already reported as potential scam.")
                .setEphemeral(true)
                .queue();
            return true;
        }

        reportedMessageToTimestamp.put(messageId, Instant.now());
        return false;
    }

    private Optional<TextChannel> findModAuditLogChannel(MessageContextInteractionEvent event) {
        Guild guild = Objects.requireNonNull(event.getGuild());
        Optional<TextChannel> modAuditLogChannel =
                Guilds.findTextChannel(guild, isModAuditLogChannel);
        if (modAuditLogChannel.isEmpty()) {
            logger.warn(
                    "Cannot find the designated mod audit log channel in guild '{}' with the pattern '{}'",
                    guild.getId(), config.getModAuditLogChannelPattern());
        }
        return modAuditLogChannel;
    }

    private MessageCreateAction reportToMods(Message message, TextChannel auditChannel) {
        User author = message.getAuthor();
        String description = createDescription(message);
        long accountAgeDays = ChronoUnit.DAYS.between(author.getTimeCreated(),
                OffsetDateTime.now(ZoneOffset.UTC));

        MessageEmbed reportEmbed = new EmbedBuilder().setTitle("Is this Scam?")
            .setDescription(
                    MessageUtils.abbreviate(description, MessageEmbed.DESCRIPTION_MAX_LENGTH))
            .setAuthor(author.getName(), null, author.getEffectiveAvatarUrl())
            .setTimestamp(message.getTimeCreated())
            .setColor(AmbientColors.MODERATION_SCAM)
            .setFooter("%s - account age %d days".formatted(author.getId(), accountAgeDays))
            .build();

        long guildId = message.getGuild().getIdLong();
        long authorId = author.getIdLong();
        String[] args = {String.valueOf(guildId), String.valueOf(authorId)};

        return auditChannel.sendMessageEmbeds(reportEmbed)
            .addActionRow(Button.success(generateComponentId(args), "Yes"),
                    Button.danger(generateComponentId(args), "No"));
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

        ButtonStyle clickedStyle = event.getButton().getStyle();
        boolean isScam = clickedStyle == ButtonStyle.SUCCESS;

        MessageEmbed resultEmbed = new EmbedBuilder()
            .setDescription(
                    isScam ? "This is scam. The user was quarantined and messages were deleted."
                            : "This is not scam, no action executed.")
            .setColor(isScam ? Color.GREEN : Color.RED)
            .build();

        List<MessageEmbed> embeds = new ArrayList<>(event.getMessage().getEmbeds());
        embeds.add(resultEmbed);

        event.editMessageEmbeds(embeds).setComponents().queue();

        if (!isScam) {
            return;
        }

        Guild guild = Objects.requireNonNull(event.getJDA().getGuildById(guildId));
        Member moderator = Objects.requireNonNull(event.getMember());

        ErrorHandler errorHandler = new ErrorHandler()
            .handle(ErrorResponse.UNKNOWN_USER, failure -> logger.debug(LogMarkers.SENSITIVE,
                    "Attempted to handle user-reported scam, but user '{}' does not exist anymore.",
                    targetId))
            .handle(ErrorResponse.UNKNOWN_MEMBER, failure -> logger.debug(LogMarkers.SENSITIVE,
                    "Attempted to handle user-reported scam, but user '{}' is not a member of guild '{}' anymore.",
                    targetId, guildId));

        guild.retrieveMemberById(targetId)
            .queue(target -> handleConfirmedScam(guild, target, moderator, event), errorHandler);
    }

    private void handleConfirmedScam(Guild guild, Member target, Member moderator,
            ButtonInteractionEvent event) {
        if (!handleCanQuarantineAndBan(guild, target, event)) {
            return;
        }

        Consumer<? super Void> onSuccess = _ -> {
        };
        Consumer<? super Throwable> onFailure = failure -> logger.warn(LogMarkers.SENSITIVE,
                "Failed to finish user-reported scam handling for user '{}' in guild '{}'.",
                target.getId(), guild.getId(), failure);

        sendQuarantineDm(target.getUser(), guild)
            .flatMap(hasSentDm -> quarantineUser(guild, target, moderator))
            .flatMap(_ -> deleteMessagesByBanAndUnban(guild, target.getUser()))
            .queue(onSuccess, onFailure);
    }

    private boolean handleCanQuarantineAndBan(Guild guild, Member target,
            ButtonInteractionEvent event) {
        Member bot = guild.getSelfMember();
        Member moderator = Objects.requireNonNull(event.getMember());
        Role quarantinedRole = ModerationUtils.getQuarantinedRole(guild, config).orElse(null);

        return ModerationUtils.handleRoleChangeChecks(quarantinedRole, "quarantine", target, bot,
                moderator, guild, ACTION_REASON, event)
                && ModerationUtils.handleHasBotPermissions("ban", Permission.BAN_MEMBERS, bot,
                        guild, event);
    }

    private RestAction<Boolean> sendQuarantineDm(User target, Guild guild) {
        String description =
                """
                        Hey there, sorry to tell you but unfortunately you have been put under quarantine after sending scam.
                        This means you can no longer interact with anyone in the server until you have been unquarantined again.

                        Potentially your account got compromised.
                        After you regained control of your account make sure you secure it properly, for example by using 2FA.
                        You can then join back our server and contact the mods to get unquarantined.""";

        return ModerationUtils.sendModActionDm(ModerationUtils.getModActionEmbed(guild,
                ACTION_TITLE, description, ACTION_REASON, true), target);
    }

    private RestAction<Void> quarantineUser(Guild guild, Member target, Member moderator) {
        logger.info(LogMarkers.SENSITIVE,
                "'{}' ({}) quarantined the user '{}' ({}) in guild '{}' for reason '{}'.",
                moderator.getUser().getName(), moderator.getId(), target.getUser().getName(),
                target.getId(), guild.getName(), ACTION_REASON);

        actionsStore.addAction(guild.getIdLong(), moderator.getIdLong(), target.getIdLong(),
                ModerationAction.QUARANTINE, null, ACTION_REASON);

        return guild
            .addRoleToMember(target,
                    ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
            .reason(ACTION_REASON);
    }

    private RestAction<Void> deleteMessagesByBanAndUnban(Guild guild, User target) {
        return guild.ban(target, 1, TimeUnit.DAYS)
            .reason(ACTION_REASON)
            .flatMap(_ -> guild.unban(target).reason(ACTION_REASON));
    }
}
