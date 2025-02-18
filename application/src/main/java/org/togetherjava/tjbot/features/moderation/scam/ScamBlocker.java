package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;
import org.togetherjava.tjbot.features.moderation.ModerationAction;
import org.togetherjava.tjbot.features.moderation.ModerationActionsStore;
import org.togetherjava.tjbot.features.moderation.ModerationUtils;
import org.togetherjava.tjbot.features.moderation.modmail.ModMailCommand;
import org.togetherjava.tjbot.features.utils.MessageUtils;
import org.togetherjava.tjbot.logging.LogMarkers;

import java.awt.Color;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages from channels, checks them for scam and takes
 * appropriate action.
 * <p>
 * If scam is detected, depending on the configuration, the blockers actions range from deleting the
 * message and banning the author to just logging the message for auditing.
 */
public final class ScamBlocker extends MessageReceiverAdapter implements UserInteractor {
    private static final Logger logger = LoggerFactory.getLogger(ScamBlocker.class);
    private static final Color AMBIENT_COLOR = Color.decode("#CFBFF5");
    private static final Set<ScamBlockerConfig.Mode> MODES_WITH_IMMEDIATE_DELETION =
            EnumSet.of(ScamBlockerConfig.Mode.AUTO_DELETE_BUT_APPROVE_QUARANTINE,
                    ScamBlockerConfig.Mode.AUTO_DELETE_AND_QUARANTINE);

    private final ScamBlockerConfig.Mode mode;
    private final String reportChannelPattern;
    private final String botTrapChannelPattern;
    private final Predicate<TextChannel> isReportChannel;
    private final Predicate<TextChannel> isBotTrapChannel;
    private final ScamDetector scamDetector;
    private final Config config;
    private final ModerationActionsStore actionsStore;
    private final ScamHistoryStore scamHistoryStore;
    private final Predicate<String> hasRequiredRole;

    private final ComponentIdInteractor componentIdInteractor;

    /**
     * Creates a new listener to receive all message sent in any channel.
     *
     * @param actionsStore to store quarantine actions in
     * @param scamHistoryStore to store and retrieve scam history from
     * @param config the config to use for this
     */
    public ScamBlocker(ModerationActionsStore actionsStore, ScamHistoryStore scamHistoryStore,
            Config config) {
        this.actionsStore = actionsStore;
        this.scamHistoryStore = scamHistoryStore;
        this.config = config;
        mode = config.getScamBlocker().getMode();
        scamDetector = new ScamDetector(config);

        reportChannelPattern = config.getScamBlocker().getReportChannelPattern();
        Predicate<String> isReportChannelName =
                Pattern.compile(reportChannelPattern).asMatchPredicate();
        isReportChannel = channel -> isReportChannelName.test(channel.getName());

        botTrapChannelPattern = config.getScamBlocker().getBotTrapChannelPattern();
        Predicate<String> isBotTrapChannelName =
                Pattern.compile(botTrapChannelPattern).asMatchPredicate();
        isBotTrapChannel = channel -> isBotTrapChannelName.test(channel.getName());

        hasRequiredRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();

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
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        if (mode == ScamBlockerConfig.Mode.OFF) {
            return;
        }

        boolean isSafe = !isBotTrapChannel.test(event.getChannel().asTextChannel());

        Message message = event.getMessage();
        String content = message.getContentDisplay();
        if (isSafe && scamDetector.isScam(content)) {
            isSafe = false;
        }

        if (isSafe) {
            return;
        }

        if (scamHistoryStore.hasRecentScamDuplicate(message)) {
            takeActionWasAlreadyReported(event);
            return;
        }

        takeAction(event);
    }

    private void takeActionWasAlreadyReported(MessageReceivedEvent event) {
        // The user recently send the same scam already, and that was already reported and handled
        addScamToHistory(event);

        boolean shouldDeleteMessage = MODES_WITH_IMMEDIATE_DELETION.contains(mode);
        if (shouldDeleteMessage) {
            deleteMessage(event);
        }
    }

    private void takeAction(MessageReceivedEvent event) {
        switch (mode) {
            case OFF -> throw new AssertionError(
                    "The OFF-mode should be detected earlier already to prevent expensive computation");
            case ONLY_LOG -> takeActionLogOnly(event);
            case APPROVE_FIRST -> takeActionApproveFirst(event);
            case AUTO_DELETE_BUT_APPROVE_QUARANTINE ->
                takeActionAutoDeleteButApproveQuarantine(event);
            case AUTO_DELETE_AND_QUARANTINE -> takeActionAutoDeleteAndQuarantine(event);
            default -> throw new IllegalArgumentException("Mode not supported: " + mode);
        }
    }

    private void takeActionLogOnly(MessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
    }

    private void takeActionApproveFirst(MessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
        reportScamMessage(event, "Is this scam?", createConfirmDialog(event));
    }

    private void takeActionAutoDeleteButApproveQuarantine(MessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
        deleteMessage(event);
        reportScamMessage(event, "Is this scam? (already deleted)", createConfirmDialog(event));
    }

    private void takeActionAutoDeleteAndQuarantine(MessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
        deleteMessage(event);
        quarantineAuthor(event);
        dmUser(event);
        reportScamMessage(event, "Detected and handled scam", List.of());
    }

    private void addScamToHistory(MessageReceivedEvent event) {
        scamHistoryStore.addScam(event.getMessage(), MODES_WITH_IMMEDIATE_DELETION.contains(mode));
    }

    private void logScamMessage(MessageReceivedEvent event) {
        logger.warn(LogMarkers.SENSITIVE,
                "Detected a scam message ('{}') from user '{}' in channel '{}' of guild '{}'.",
                event.getMessageId(), event.getAuthor().getId(), event.getChannel().getId(),
                event.getGuild().getId());
    }

    private void deleteMessage(MessageReceivedEvent event) {
        event.getMessage().delete().queue();
    }

    private void quarantineAuthor(MessageReceivedEvent event) {
        quarantineAuthor(event.getGuild(), event.getMember(), event.getJDA().getSelfUser());
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

    private void reportScamMessage(MessageReceivedEvent event, String reportTitle,
            List<? extends Button> confirmDialog) {
        Guild guild = event.getGuild();
        Optional<TextChannel> reportChannel = getReportChannel(guild);
        if (reportChannel.isEmpty()) {
            logger.warn(
                    "Unable to report a scam message, did not find a report channel matching the configured pattern '{}' for guild '{}'",
                    reportChannelPattern, guild.getName());
            return;
        }

        User author = event.getAuthor();
        String avatarOrDefaultUrl = author.getEffectiveAvatarUrl();

        MessageEmbed embed =
                new EmbedBuilder().setDescription(event.getMessage().getContentStripped())
                    .setTitle(reportTitle)
                    .setAuthor(author.getName(), null, avatarOrDefaultUrl)
                    .setTimestamp(event.getMessage().getTimeCreated())
                    .setColor(AMBIENT_COLOR)
                    .setFooter(author.getId())
                    .build();

        MessageCreateBuilder messageBuilder = new MessageCreateBuilder().setEmbeds(embed);
        if (!confirmDialog.isEmpty()) {
            messageBuilder.setActionRow(confirmDialog);
        }
        MessageCreateData message = messageBuilder.build();

        reportChannel.orElseThrow().sendMessage(message).queue();
    }

    private void dmUser(MessageReceivedEvent event) {
        dmUser(event.getGuild(), event.getAuthor().getIdLong(), event.getJDA());
    }

    private void dmUser(Guild guild, long userId, JDA jda) {
        jda.openPrivateChannelById(userId).flatMap(channel -> dmUser(guild, channel)).queue(any -> {
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
        return guild.getTextChannelCache().stream().filter(isReportChannel).findAny();
    }

    private List<Button> createConfirmDialog(MessageReceivedEvent event) {
        ComponentIdArguments args = new ComponentIdArguments(mode, event.getGuild().getIdLong(),
                event.getChannel().getIdLong(), event.getMessageIdLong(),
                event.getAuthor().getIdLong(),
                ScamHistoryStore.hashMessageContent(event.getMessage()));

        return List.of(Button.success(generateComponentId(args), "Yes"),
                Button.danger(generateComponentId(args), "No"));
    }

    private String generateComponentId(ComponentIdArguments args) {
        return componentIdInteractor.generateComponentId(args.toArray());
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> argsRaw) {
        ComponentIdArguments args = ComponentIdArguments.fromList(argsRaw);
        if (event.getMember().getRoles().stream().map(Role::getName).noneMatch(hasRequiredRole)) {
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
