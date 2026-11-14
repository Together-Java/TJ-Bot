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
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
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

import javax.annotation.Nullable;

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
 * <li><b>Ban</b>: the author is banned for a custom reason and duration, optionally deleting their
 * recent messages.</li>
 * <li><b>Warn</b>: the author is warned for a custom reason.</li>
 * <li><b>Dismiss</b>: nothing happens, the alert is closed.</li>
 * </ul>
 * For Scam and Job-Ad, the ban and unban only serves to delete the messages, the user is free to
 * join back afterward.
 */
public final class ReportCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final Logger logger = LoggerFactory.getLogger(ReportCommand.class);

    private static final String COMMAND_NAME = "report";
    private static final String REASON_INPUT = "reason";
    private static final String DURATION_INPUT = "duration";
    private static final String DELETE_HISTORY_INPUT = "delete-history";

    private static final String SCAM_REASON = "Message was reported and confirmed as scam";
    private static final String JOB_AD_REASON =
            "Message was reported and confirmed as a job advertisement";

    private static final String FAILED_MESSAGE =
            "Sorry, there was an issue forwarding your report to the moderators. We are investigating.";

    private static final Duration USER_COMMAND_COOLDOWN = Duration.ofSeconds(15);

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
    public ReportCommand(Config config, ModerationActionsStore actionsStore) {
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

        Optional<TextChannel> modChannel = findModChannel(event);
        if (modChannel.isEmpty()) {
            event.reply(FAILED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        if (handleWasAlreadyReportedMessage(event)) {
            return;
        }

        Message message = event.getTarget();
        reportToMods(message, modChannel.orElseThrow()).mapToResult().map(result -> {
            if (result.isFailure()) {
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

        int modActionCount = actionsStore
            .getActionsByTargetAscending(message.getGuildIdLong(), author.getIdLong())
            .size();
        String modActionsText = modActionCount == 0 ? "no" : String.valueOf(modActionCount);

        MessageEmbed reportEmbed = new EmbedBuilder().setTitle("Message Reported")
            .setDescription(
                    MessageUtils.abbreviate(description, MessageEmbed.DESCRIPTION_MAX_LENGTH))
            .setAuthor(author.getName(), null, author.getEffectiveAvatarUrl())
            .setTimestamp(message.getTimeCreated())
            .setColor(AmbientColors.MODERATION_SCAM)
            .setFooter("%s - account age %d days - %s actions against user"
                .formatted(author.getId(), accountAgeDays, modActionsText))
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
                    Button.primary(generateComponentId(guildId, authorId, ReportAction.WARN.name()),
                            "Warn"),
                    Button.primary(generateComponentId(guildId, authorId, ReportAction.BAN.name()),
                            "Ban"))
            .addActionRow(Button.danger(
                    generateComponentId(guildId, authorId, ReportAction.DISMISS.name()), "Dismiss"),
                    Button.primary(generateComponentId(guildId, authorId, ReportAction.AUDIT.name(),
                            String.valueOf(0)), "Audit"),
                    Button.link(message.getJumpUrl(), "Message"));
    }

    private static String createDescription(Message target) {
        String content = target.getContentStripped();
        String description = content.isBlank() ? "(message had no text content)" : content;

        List<Message.Attachment> attachments = target.getAttachments();
        if (!attachments.isEmpty()) {
            String attachmentText = attachments.stream()
                .map(Message.Attachment::getFileName)
                .collect(Collectors.joining("\n- ", "\n- ", ""));
            description += """


                    Attachments: %s
                    """.formatted(attachmentText);
        }

        return description;
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        long guildId = Long.parseLong(args.get(0));
        long targetId = Long.parseLong(args.get(1));
        ReportAction action = ReportAction.valueOf(args.get(2));

        if (action == ReportAction.BAN || action == ReportAction.WARN) {
            openModerationModal(event, guildId, targetId, action);
            return;
        }

        if (action == ReportAction.AUDIT || action == ReportAction.AUDIT_PAGE) {
            int page = Integer.parseInt(args.get(3));
            onAuditButtonClick(event, guildId, targetId, page, action == ReportAction.AUDIT_PAGE);
            return;
        }

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

    private void openModerationModal(ButtonInteractionEvent event, long guildId, long targetId,
            ReportAction action) {
        if (action == ReportAction.BAN && !ModerationUtils.handleHasAuthorPermissions("ban",
                Permission.BAN_MEMBERS, Objects.requireNonNull(event.getMember()), event)) {
            return;
        }

        TextInput reason = TextInput.create(REASON_INPUT, "Reason", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Why should this user be "
                    + (action == ReportAction.BAN ? "banned?" : "warned?"))
            .setRequired(true)
            .setMaxLength(512)
            .build();
        Modal.Builder modal = Modal
            .create(generateComponentId(String.valueOf(guildId), String.valueOf(targetId),
                    action.name()),
                    action == ReportAction.BAN ? "Ban reported user" : "Warn reported user")
            .addActionRow(reason);
        if (action == ReportAction.BAN) {
            modal.addActionRow(TextInput
                .create(DURATION_INPUT, "Duration (0, h or d)", TextInputStyle.SHORT)
                .setPlaceholder("Hours or days: 1h, 3h, 1d, 7d, 30d; empty or 0 = permanent")
                .setRequired(false)
                .setMaxLength(20)
                .build());
            modal.addActionRow(TextInput
                .create(DELETE_HISTORY_INPUT, "Delete messages (days)", TextInputStyle.SHORT)
                .setPlaceholder("Empty or 0 = none, 1 = day, 7 = week")
                .setValue("1")
                .setRequired(false)
                .setMaxLength(1)
                .build());
        }
        event.replyModal(modal.build()).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        long guildId = Long.parseLong(args.get(0));
        long targetId = Long.parseLong(args.get(1));
        ReportAction action = ReportAction.valueOf(args.get(2));
        if (action != ReportAction.BAN && action != ReportAction.WARN) {
            throw new IllegalArgumentException("Unexpected modal action: " + action);
        }
        Guild guild = Objects.requireNonNull(event.getGuild());
        if (guild.getIdLong() != guildId) {
            event.reply("This report belongs to a different server.").setEphemeral(true).queue();
            return;
        }
        Member moderator = Objects.requireNonNull(event.getMember());
        String reason = Objects.requireNonNull(event.getValue(REASON_INPUT)).getAsString().strip();
        if (reason.isBlank()) {
            event.reply("Please enter a reason.").setEphemeral(true).queue();
            return;
        }
        if (!ModerationUtils.handleReason(reason, event)) {
            return;
        }
        if (action == ReportAction.BAN && (!ModerationUtils.handleHasAuthorPermissions("ban",
                Permission.BAN_MEMBERS, moderator, event)
                || !ModerationUtils.handleHasBotPermissions("ban", Permission.BAN_MEMBERS,
                        guild.getSelfMember(), guild, event))) {
            return;
        }
        Optional<ModerationUtils.TemporaryData> temporaryData;
        try {
            temporaryData = action == ReportAction.BAN
                    ? ModerationUtils.parseBanDuration(
                            Objects.requireNonNull(event.getValue(DURATION_INPUT)).getAsString())
                    : Optional.empty();
        } catch (IllegalArgumentException _) {
            event.reply(
                    "Invalid duration. Use a positive number followed by h or d (1h, 3h, 1d, 7d). Leave empty or enter 0 for permanent.")
                .setEphemeral(true)
                .queue();
            return;
        }

        int deleteHistoryDays = readDeleteHistoryDays(event, action);
        if (deleteHistoryDays == -1) {
            return;
        }

        ModalActionData actionData =
                new ModalActionData(action, temporaryData.orElse(null), reason, deleteHistoryDays);
        event.deferReply(true)
            .queue(hook -> loadModalTarget(event, hook, guild, moderator, targetId, actionData));
    }

    private int readDeleteHistoryDays(ModalInteractionEvent event, ReportAction action) {
        if (action != ReportAction.BAN) {
            return 0;
        }
        String deleteHistory =
                Objects.requireNonNull(event.getValue(DELETE_HISTORY_INPUT)).getAsString().strip();
        int deleteHistoryDays = switch (deleteHistory) {
            case "", "0" -> 0;
            case "1" -> 1;
            case "7" -> 7;
            default -> -1;
        };
        if (deleteHistoryDays == -1) {
            event.reply(
                    "Invalid message deletion option. Leave empty or enter 0 for none, 1 for one day, or 7 for one week.")
                .setEphemeral(true)
                .queue();
        }
        return deleteHistoryDays;
    }

    private void loadModalTarget(ModalInteractionEvent event, InteractionHook hook, Guild guild,
            Member moderator, long targetId, ModalActionData actionData) {
        guild.retrieveMemberById(targetId).mapToResult().flatMap(result -> {
            if (result.isSuccess()) {
                Member target = result.get();
                if (!moderator.canInteract(target) || !guild.getSelfMember().canInteract(target)) {
                    return hook.sendMessage(
                            "You or the bot cannot moderate this user because of their role hierarchy.")
                        .setEphemeral(true);
                }
            } else if (!(result.getFailure() instanceof ErrorResponseException failure)
                    || failure.getErrorResponse() != ErrorResponse.UNKNOWN_MEMBER) {
                logger.warn(LogMarkers.SENSITIVE,
                        "Failed to retrieve reported member '{}' in guild '{}'.", targetId,
                        guild.getIdLong(), result.getFailure());
                return hook.sendMessage("Could not load the reported user. Please check the logs.")
                    .setEphemeral(true);
            }
            return event.getJDA().retrieveUserById(targetId).map(target -> {
                executeModalAction(event, hook, guild, moderator, target, actionData);
                return null;
            });
        }).queue(null, failure -> {
            logger.warn(LogMarkers.SENSITIVE, "Failed to load reported user '{}' in guild '{}'.",
                    targetId, guild.getIdLong(), failure);
            hook.sendMessage("Could not load the reported user. Please check the logs.")
                .setEphemeral(true)
                .queue();
        });
    }

    private void executeModalAction(ModalInteractionEvent event, InteractionHook hook, Guild guild,
            Member moderator, User target, ModalActionData actionData) {
        Message report = Objects.requireNonNull(event.getMessage());
        if (report.getComponents().isEmpty()) {
            hook.sendMessage("This report has already been handled.").setEphemeral(true).queue();
            return;
        }
        Consumer<MessageEmbed> onSuccess = feedback -> {
            List<MessageEmbed> embeds = new ArrayList<>(report.getEmbeds());
            embeds.add(feedback);
            report.editMessageEmbeds(embeds).setComponents().queue(null, failure -> {
                logger.warn("The moderation action succeeded, but updating report '{}' failed.",
                        report.getId(), failure);
                hook.sendMessage("The action succeeded, but the report could not be updated.")
                    .setEphemeral(true)
                    .queue();
            });
        };
        if (actionData.action() == ReportAction.BAN) {
            guild.retrieveBan(target).mapToResult().queue(result -> {
                if (result.isSuccess()) {
                    hook.sendMessage("This user is already banned.").setEphemeral(true).queue();
                } else if (result.getFailure() instanceof ErrorResponseException failure
                        && failure.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                    handleModalAction(hook, guild, moderator, target, actionData, onSuccess);
                } else {
                    logger.warn(LogMarkers.SENSITIVE, "Could not check ban for reported user '{}'.",
                            target.getId(), result.getFailure());
                    hook.sendMessage("Could not check whether this user is already banned.")
                        .setEphemeral(true)
                        .queue();
                }
            });
        } else {
            handleModalAction(hook, guild, moderator, target, actionData, onSuccess);
        }
    }

    private void handleModalAction(InteractionHook hook, Guild guild, Member moderator, User target,
            ModalActionData actionData, Consumer<MessageEmbed> onSuccess) {
        RestAction<MessageEmbed> handling = actionData.action() == ReportAction.BAN
                ? ModerationUtils.banUser(guild, target, moderator, actionData.temporaryData(),
                        actionData.reason(), actionData.deleteHistoryDays(), actionsStore)
                : ModerationUtils.warnUser(guild, target, moderator, actionData.reason(),
                        actionsStore);
        handling.queue(feedback -> {
            onSuccess.accept(feedback);
            hook.sendMessageEmbeds(feedback).setEphemeral(true).queue();
        }, failure -> {
            logger.warn(LogMarkers.SENSITIVE, "Failed to handle reported user '{}' in guild '{}'.",
                    target.getId(), guild.getId(), failure);
            hook.sendMessage(
                    "The action failed. Please check the logs and handle the user manually.")
                .setEphemeral(true)
                .queue();
        });
    }

    private void onAuditButtonClick(ButtonInteractionEvent event, long guildId, long targetId,
            int targetPage, boolean isPageButton) {
        if (isPageButton) {
            event.deferEdit().queue(_ -> loadAuditPage(event, guildId, targetId, targetPage));
        } else {
            event.deferReply(true).queue(_ -> loadAuditPage(event, guildId, targetId, targetPage));
        }
    }

    private void loadAuditPage(ButtonInteractionEvent event, long guildId, long targetId,
            int targetPage) {
        List<ActionRecord> actions =
                new ArrayList<>(actionsStore.getActionsByTargetAscending(guildId, targetId));
        List<List<ActionRecord>> pages = ModerationUtils.groupActionsByPages(actions);

        event.getJDA()
            .retrieveUserById(targetId)
            .flatMap(user -> prepareAuditEmbedTasks(event, user, guildId, actions, pages,
                    targetPage))
            .onErrorFlatMap(_ -> event.getHook()
                .editOriginal("Could not load audit data for this user.")
                .map(_ -> null))
            .queue();
    }

    private RestAction<List<MessageEmbed.Field>> prepareAuditEmbedTasks(
            ButtonInteractionEvent event, User user, long guildId, List<ActionRecord> actions,
            List<List<ActionRecord>> pages, int targetPage) {

        EmbedBuilder auditEmbed =
                new EmbedBuilder().setTitle("Audit log of **%s**".formatted(user.getName()))
                    .setAuthor(user.getName(), null, user.getEffectiveAvatarUrl())
                    .setColor(AmbientColors.MODERATION)
                    .setDescription(ModerationUtils.createSummaryMessageDescription(actions));

        if (pages.isEmpty()) {
            return event.getHook()
                .editOriginalEmbeds(auditEmbed.build())
                .setComponents(List.of())
                .map(_ -> List.of());
        }

        int currentPageIndex = Math.clamp(targetPage, 0, pages.size() - 1);

        List<RestAction<MessageEmbed.Field>> fetchFieldActions = pages.get(currentPageIndex)
            .stream()
            .map(action -> ModerationUtils.moderationActionToEmbedField(action, event.getJDA()))
            .toList();

        return RestAction.allOf(fetchFieldActions).map(embedFields -> {
            finalizeAndSendAuditEmbed(event, auditEmbed, embedFields, guildId, user.getIdLong(),
                    currentPageIndex, pages.size());
            return embedFields;
        });
    }

    private void finalizeAndSendAuditEmbed(ButtonInteractionEvent event, EmbedBuilder auditEmbed,
            List<MessageEmbed.Field> embedFields, long guildId, long targetId, int currentPageIndex,
            int totalPages) {
        auditEmbed.clearFields();
        embedFields.forEach(auditEmbed::addField);

        auditEmbed.setFooter("Page %d/%d".formatted(currentPageIndex + 1, totalPages));

        String prevButtonId = generateComponentId(String.valueOf(guildId), String.valueOf(targetId),
                ReportAction.AUDIT_PAGE.name(), String.valueOf(currentPageIndex - 1));
        String nextButtonId = generateComponentId(String.valueOf(guildId), String.valueOf(targetId),
                ReportAction.AUDIT_PAGE.name(), String.valueOf(currentPageIndex + 1));

        Button prevButton = Button.primary(prevButtonId, "⬅").withDisabled(currentPageIndex == 0);
        Button nextButton =
                Button.primary(nextButtonId, "➡").withDisabled(currentPageIndex == totalPages - 1);

        event.getHook()
            .editOriginalEmbeds(auditEmbed.build())
            .setActionRow(prevButton, nextButton)
            .queue();
    }

    private void handleConfirmedViolation(ButtonInteractionEvent event, ReportAction action,
            Guild guild, Member moderator, Member target, List<MessageEmbed> alertEmbeds) {

        Member bot = guild.getSelfMember();

        boolean canSanctionUser = switch (action) {
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

        canSanctionUser &= ModerationUtils.handleHasBotPermissions("ban", Permission.BAN_MEMBERS,
                bot, guild, event);

        if (!canSanctionUser) {
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
                        Hiring, Recruiting, seeking for jobs or anything alike is not allowed in this server.

                        In order to automatically remove your messages you were briefly removed from the server, you are welcome to join back.""";

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

    private record ModalActionData(ReportAction action,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason,
            int deleteHistoryDays) {
    }

    private enum ReportAction {
        SCAM,
        JOB_AD,
        BAN,
        WARN,
        DISMISS,
        AUDIT,
        AUDIT_PAGE,
    }
}
