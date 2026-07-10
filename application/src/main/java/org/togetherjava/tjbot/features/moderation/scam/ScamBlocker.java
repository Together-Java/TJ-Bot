package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.analytics.Metrics;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;
import org.togetherjava.tjbot.features.moderation.ModerationAction;
import org.togetherjava.tjbot.features.moderation.ModerationActionsStore;
import org.togetherjava.tjbot.features.moderation.ModerationUtils;
import org.togetherjava.tjbot.features.moderation.modmail.ModMailCommand;
import org.togetherjava.tjbot.features.utils.AmbientColors;
import org.togetherjava.tjbot.features.utils.Guilds;
import org.togetherjava.tjbot.features.utils.MessageUtils;
import org.togetherjava.tjbot.logging.LogMarkers;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Listener that receives all sent messages from channels, checks them for scam and takes
 * appropriate action.
 * <p>
 * If scam is detected, depending on the configuration, the blockers actions range from deleting the
 * message and banning the author to just logging the message for auditing.
 */
public final class ScamBlocker extends MessageReceiverAdapter implements UserInteractor {
    private static final Logger logger = LoggerFactory.getLogger(ScamBlocker.class);
    private static final Set<ScamBlockerConfig.Mode> MODES_WITH_IMMEDIATE_DELETION =
            EnumSet.of(ScamBlockerConfig.Mode.AUTO_DELETE_BUT_APPROVE_QUARANTINE,
                    ScamBlockerConfig.Mode.AUTO_DELETE_AND_QUARANTINE);

    private final ScamBlockerConfig.Mode mode;
    private final String reportChannelPattern;
    private final String botTrapChannelPattern;
    private final Predicate<String> isReportChannelName;
    private final Predicate<TextChannel> isBotTrapChannel;
    private final ScamDetector scamDetector;
    private final Config config;
    private final ModerationActionsStore actionsStore;
    private final ScamHistoryStore scamHistoryStore;
    private final Predicate<String> isRequiredRole;

    private final Metrics metrics;
    private final ComponentIdInteractor componentIdInteractor;

    /**
     * Creates a new listener to receive all message sent in any channel.
     *
     * @param actionsStore to store quarantine actions in
     * @param scamHistoryStore to store and retrieve scam history from
     * @param config the config to use for this
     * @param metrics to track events
     */
    public ScamBlocker(ModerationActionsStore actionsStore, ScamHistoryStore scamHistoryStore,
            Config config, Metrics metrics) {
        this.actionsStore = actionsStore;
        this.scamHistoryStore = scamHistoryStore;
        this.config = config;
        mode = config.getScamBlocker().getMode();
        scamDetector = new ScamDetector(config);

        reportChannelPattern = config.getScamBlocker().getReportChannelPattern();
        isReportChannelName = Pattern.compile(reportChannelPattern).asMatchPredicate();

        botTrapChannelPattern = config.getScamBlocker().getBotTrapChannelPattern();
        Predicate<String> isBotTrapChannelName =
                Pattern.compile(botTrapChannelPattern).asMatchPredicate();
        isBotTrapChannel = channel -> isBotTrapChannelName.test(channel.getName());

        isRequiredRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();

        this.metrics = metrics;
        componentIdInteractor = new ComponentIdInteractor(getInteractionType(), getName());
    }

    @Override
    public String getName() {
        return "scam-blocker";
    }

    @Override
    public UserInteractionType getInteractionType() {
        return UserInteractionType.OTHER;
    }

    @Override
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        handleMessage(event.getMessage(), event.getGuild());
    }

    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        handleMessage(event.getMessage(), event.getGuild());
    }

    private void handleMessage(Message message, Guild guild) {
        if (message.getAuthor().isBot() || message.isWebhookMessage()) {
            return;
        }

        if (mode == ScamBlockerConfig.Mode.OFF) {
            return;
        }

        boolean isSafe = true;
        if (message.getChannel() instanceof TextChannel textChannel
                && isBotTrapChannel.test(textChannel)) {
            isSafe = false;
        }

        if (isSafe && scamDetector.isScam(message)) {
            isSafe = false;
        }

        if (isSafe) {
            return;
        }

        if (scamHistoryStore.hasRecentScamDuplicate(message)) {
            takeActionWasAlreadyReported(message, guild);
            return;
        }

        takeAction(message, guild);
    }

    private void takeActionWasAlreadyReported(Message message, Guild guild) {
        // The user recently send the same scam already, and that was already reported and handled
        addScamToHistory(message);

        boolean shouldDeleteMessage = MODES_WITH_IMMEDIATE_DELETION.contains(mode);
        if (shouldDeleteMessage) {
            deleteMessage(message);
        }
    }

    private void takeAction(Message message, Guild guild) {
        metrics.count("scam-detected");
        switch (mode) {
            case OFF -> throw new AssertionError(
                    "The OFF-mode should be detected earlier already to prevent expensive computation");
            case ONLY_LOG -> takeActionLogOnly(message, guild);
            case APPROVE_FIRST -> takeActionApproveFirst(message, guild);
            case AUTO_DELETE_BUT_APPROVE_QUARANTINE ->
                takeActionAutoDeleteButApproveQuarantine(message, guild);
            case AUTO_DELETE_AND_QUARANTINE -> takeActionAutoDeleteAndQuarantine(message, guild);
            default -> throw new IllegalArgumentException("Mode not supported: " + mode);
        }
    }

    private void takeActionLogOnly(Message message, Guild guild) {
        addScamToHistory(message);
        logScamMessage(message);
    }

    private void takeActionApproveFirst(Message message, Guild guild) {
        addScamToHistory(message);
        logScamMessage(message);
        reportScamMessage(message, guild, "Is this scam?", createConfirmDialog(message, guild));
    }

    private void takeActionAutoDeleteButApproveQuarantine(Message message, Guild guild) {
        addScamToHistory(message);
        logScamMessage(message);
        deleteMessage(message);
        reportScamMessage(message, guild, "Is this scam? (already deleted)",
                createConfirmDialog(message, guild));
    }

    private void takeActionAutoDeleteAndQuarantine(Message message, Guild guild) {
        addScamToHistory(message);
        logScamMessage(message);
        deleteMessage(message);
        quarantineAuthor(message, guild);
        dmUser(message, guild);
        reportScamMessage(message, guild, "Detected and handled scam", List.of());
    }

    private void addScamToHistory(Message message) {
        scamHistoryStore.addScam(message, MODES_WITH_IMMEDIATE_DELETION.contains(mode));
    }

    private void logScamMessage(Message message) {
        logger.warn(LogMarkers.SENSITIVE,
                "Detected a scam message ('{}') from user '{}' in channel '{}' of guild '{}'.",
                message.getId(), message.getAuthor().getId(), message.getChannel().getId(),
                message.getGuild().getId());
    }

    private void deleteMessage(Message message) {
        message.delete().queue();
    }

    private void quarantineAuthor(Message message, Guild guild) {
        quarantineAuthor(guild,
                Objects.requireNonNull(message.getMember(), "Author must not be null"),
                message.getJDA().getSelfUser());
    }

    private void quarantineAuthor(Guild guild, Member author, SelfUser bot) {
        String reason = "User posted scam that was automatically detected";

        actionsStore.addAction(guild.getIdLong(), bot.getIdLong(), author.getIdLong(),
                ModerationAction.QUARANTINE, null, reason);

        guild
            .addRoleToMember(author,
                    ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
            .reason(reason)
            .queue();
    }

    private void reportScamMessage(Message message, Guild guild, String reportTitle,
            List<? extends Button> confirmDialog) {
        Optional<TextChannel> reportChannel = getReportChannel(guild);
        if (reportChannel.isEmpty()) {
            logger.warn(
                    "Unable to report a scam message, did not find a report channel matching the configured pattern '{}' for guild '{}'",
                    reportChannelPattern, guild.getName());
            return;
        }

        User author = message.getAuthor();
        String avatarOrDefaultUrl = author.getEffectiveAvatarUrl();
        String content = message.getContentStripped();
        List<Message.Attachment> attachments = message.getAttachments();

        if (!attachments.isEmpty()) {
            String attachmentInfo = attachments.stream()
                .map(Message.Attachment::getFileName)
                .collect(Collectors.joining(", "));
            content += "%s(The message has %d attachment%s: %s)".formatted(
                    content.isBlank() ? "" : "\n", attachments.size(),
                    attachments.size() > 1 ? "s " : "", attachmentInfo);
        }
        MessageEmbed embed = new EmbedBuilder().setDescription(content)
            .setTitle(reportTitle)
            .setAuthor(author.getName(), null, avatarOrDefaultUrl)
            .setTimestamp(message.getTimeCreated())
            .setColor(AmbientColors.MODERATION_SCAM)
            .setFooter(author.getId())
            .build();

        MessageCreateBuilder messageBuilder = new MessageCreateBuilder().setEmbeds(embed);
        if (!confirmDialog.isEmpty()) {
            messageBuilder.setActionRow(confirmDialog);
        }
        MessageCreateData messageData = messageBuilder.build();

        reportChannel.orElseThrow().sendMessage(messageData).queue();
    }

    private void dmUser(Message message, Guild guild) {
        dmUser(guild, message.getAuthor().getIdLong(), message.getJDA());
    }

    private void dmUser(Guild guild, long userId, JDA jda) {
        jda.openPrivateChannelById(userId).flatMap(channel -> dmUser(guild, channel)).queue(_ -> {
        }, failure -> logger.debug(
                "Unable to send dm message to user {} in guild {} to inform them about a scam message being blocked",
                userId, guild.getId(), failure));
    }

    private RestAction<Message> dmUser(Guild guild, PrivateChannel channel) {
        UnaryOperator<String> createDmMessage =
                commandMention -> """
                        Hey there, we detected that you did send scam in the server %s and therefore put you under quarantine.
                        This means you can no longer interact with anyone in the server until you have been unquarantined again.

                        If you think this was a mistake (for example, your account was hacked, but you got back control over it),
                        you can get in touch with a moderator by using the %s command. \
                        Your message will then be forwarded and a moderator will get back to you soon 👍
                        """
                    .formatted(guild.getName(), commandMention);

        return MessageUtils.mentionGlobalSlashCommand(guild.getJDA(), ModMailCommand.COMMAND_NAME)
            .map(createDmMessage)
            .flatMap(channel::sendMessage);
    }

    private Optional<TextChannel> getReportChannel(Guild guild) {
        return Guilds.findTextChannel(guild, isReportChannelName);
    }

    private List<Button> createConfirmDialog(Message message, Guild guild) {
        ComponentIdArguments args = new ComponentIdArguments(mode, guild.getIdLong(),
                message.getChannel().getIdLong(), message.getIdLong(),
                message.getAuthor().getIdLong(), ScamHistoryStore.hashMessageContent(message));

        return List.of(Button.success(generateComponentId(args), "Yes"),
                Button.danger(generateComponentId(args), "No"));
    }

    private String generateComponentId(ComponentIdArguments args) {
        return componentIdInteractor.generateComponentId(args.toArray());
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> argsRaw) {
        ComponentIdArguments args = ComponentIdArguments.fromList(argsRaw);
        if (Guilds.doesMemberNotHaveRole(Objects.requireNonNull(event.getMember()),
                isRequiredRole)) {
            event.reply(
                    "You can not handle scam in this guild, since you do not have the required role.")
                .setEphemeral(true)
                .queue();
            return;
        }

        MessageUtils.disableButtons(event.getMessage());
        event.deferEdit().queue();
        if (event.getButton().getStyle() == ButtonStyle.DANGER) {
            logger.info(LogMarkers.SENSITIVE,
                    "Identified a false-positive scam (id '{}', hash '{}') in guild '{}' sent by author '{}'",
                    args.messageId, args.contentHash, args.guildId, args.authorId);
            return;
        }

        Guild guild = event.getJDA().getGuildById(args.guildId);
        if (guild == null) {
            logger.debug(
                    "Attempted to handle scam, but the bot is not connected to the guild '{}' anymore, skipping scam handling.",
                    args.guildId);
            return;
        }

        Consumer<Member> onRetrieveAuthorSuccess = author -> {
            quarantineAuthor(guild, author, event.getJDA().getSelfUser());
            dmUser(guild, args.authorId, event.getJDA());

            // Delete all messages like this
            Collection<ScamHistoryStore.ScamIdentification> scamMessages = scamHistoryStore
                .markScamDuplicatesDeleted(args.guildId, args.authorId, args.contentHash);

            scamMessages.forEach(scamMessage -> {
                TextChannel channel = guild.getTextChannelById(scamMessage.channelId());
                if (channel == null) {
                    logger.debug(
                            "Attempted to delete scam messages, but the channel '{}' does not exist anymore, skipping deleting messages for this channel.",
                            scamMessage.channelId());
                    return;
                }

                channel.deleteMessageById(scamMessage.messageId()).mapToResult().queue();
            });
        };

        Consumer<Throwable> onRetrieveAuthorFailure = new ErrorHandler()
            .handle(ErrorResponse.UNKNOWN_USER,
                    failure -> logger.debug(LogMarkers.SENSITIVE,
                            "Attempted to handle scam, but user '{}' does not exist anymore.",
                            args.authorId))
            .handle(ErrorResponse.UNKNOWN_MEMBER, failure -> logger.debug(LogMarkers.SENSITIVE,
                    "Attempted to handle scam, but user '{}' is not a member of the guild anymore.",
                    args.authorId));

        guild.retrieveMemberById(args.authorId)
            .queue(onRetrieveAuthorSuccess, onRetrieveAuthorFailure);
    }

    private record ComponentIdArguments(ScamBlockerConfig.Mode mode, long guildId, long channelId,
            long messageId, long authorId, String contentHash) {

        static ComponentIdArguments fromList(List<String> args) {
            ScamBlockerConfig.Mode mode = ScamBlockerConfig.Mode.valueOf(args.getFirst());
            long guildId = Long.parseLong(args.get(1));
            long channelId = Long.parseLong(args.get(2));
            long messageId = Long.parseLong(args.get(3));
            long authorId = Long.parseLong(args.get(4));
            String contentHash = args.get(5);
            return new ComponentIdArguments(mode, guildId, channelId, messageId, authorId,
                    contentHash);
        }

        String[] toArray() {
            return new String[] {mode.name(), Long.toString(guildId), Long.toString(channelId),
                    Long.toString(messageId), Long.toString(authorId), contentHash};
        }
    }
}
