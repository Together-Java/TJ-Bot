package org.togetherjava.tjbot.commands.moderation.scam;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.commands.UserInteractor;
import org.togetherjava.tjbot.commands.componentids.ComponentId;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.commands.componentids.Lifespan;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;
import org.togetherjava.tjbot.commands.moderation.ModerationActionsStore;
import org.togetherjava.tjbot.commands.moderation.ModerationUtils;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;

import java.awt.Color;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
    private final Predicate<TextChannel> isReportChannel;
    private final ScamDetector scamDetector;
    private final Config config;
    private final ModerationActionsStore actionsStore;
    private final ScamHistoryStore scamHistoryStore;
    private final Predicate<String> hasRequiredRole;

    private ComponentIdGenerator componentIdGenerator;

    /**
     * Creates a new listener to receive all message sent in any channel.
     *
     * @param actionsStore to store quarantine actions in
     * @param scamHistoryStore to store and retrieve scam history from
     * @param config the config to use for this
     */
    public ScamBlocker(@NotNull ModerationActionsStore actionsStore,
            @NotNull ScamHistoryStore scamHistoryStore, @NotNull Config config) {
        super(Pattern.compile(".*"));

        this.actionsStore = actionsStore;
        this.scamHistoryStore = scamHistoryStore;
        this.config = config;
        mode = config.getScamBlocker().getMode();
        scamDetector = new ScamDetector(config);

        reportChannelPattern = config.getScamBlocker().getReportChannelPattern();
        Predicate<String> isReportChannelName =
                Pattern.compile(reportChannelPattern).asMatchPredicate();
        isReportChannel = channel -> isReportChannelName.test(channel.getName());
        hasRequiredRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();
    }

    @Override
    public @NotNull String getName() {
        return "scam-blocker";
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void acceptComponentIdGenerator(@NotNull ComponentIdGenerator generator) {
        componentIdGenerator = generator;
    }

    @Override
    public void onMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        if (mode == ScamBlockerConfig.Mode.OFF) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentDisplay();
        if (!scamDetector.isScam(content)) {
            return;
        }

        if (scamHistoryStore.hasRecentScamDuplicate(message)) {
            takeActionWasAlreadyReported(event);
            return;
        }

        takeAction(event);
    }

    private void takeActionWasAlreadyReported(@NotNull GuildMessageReceivedEvent event) {
        // The user recently send the same scam already, and that was already reported and handled
        addScamToHistory(event);

        boolean shouldDeleteMessage = MODES_WITH_IMMEDIATE_DELETION.contains(mode);
        if (shouldDeleteMessage) {
            deleteMessage(event);
        }
    }

    private void takeAction(@NotNull GuildMessageReceivedEvent event) {
        switch (mode) {
            case OFF -> throw new AssertionError(
                    "The OFF-mode should be detected earlier already to prevent expensive computation");
            case ONLY_LOG -> takeActionLogOnly(event);
            case APPROVE_FIRST -> takeActionApproveFirst(event);
            case AUTO_DELETE_BUT_APPROVE_QUARANTINE -> takeActionAutoDeleteButApproveQuarantine(
                    event);
            case AUTO_DELETE_AND_QUARANTINE -> takeActionAutoDeleteAndQuarantine(event);
            default -> throw new IllegalArgumentException("Mode not supported: " + mode);
        }
    }

    private void takeActionLogOnly(@NotNull GuildMessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
    }

    private void takeActionApproveFirst(@NotNull GuildMessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
        reportScamMessage(event, "Is this scam?", createConfirmDialog(event));
    }

    private void takeActionAutoDeleteButApproveQuarantine(
            @NotNull GuildMessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
        deleteMessage(event);
        reportScamMessage(event, "Is this scam? (already deleted)", createConfirmDialog(event));
    }

    private void takeActionAutoDeleteAndQuarantine(@NotNull GuildMessageReceivedEvent event) {
        addScamToHistory(event);
        logScamMessage(event);
        deleteMessage(event);
        quarantineAuthor(event);
        dmUser(event);
        reportScamMessage(event, "Detected and handled scam", null);
    }

    private void addScamToHistory(@NotNull GuildMessageReceivedEvent event) {
        scamHistoryStore.addScam(event.getMessage(), MODES_WITH_IMMEDIATE_DELETION.contains(mode));
    }

    private void logScamMessage(@NotNull GuildMessageReceivedEvent event) {
        logger.warn("Detected a scam message ('{}') from user '{}' in channel '{}' of guild '{}'.",
                event.getMessageId(), event.getAuthor().getId(), event.getChannel().getId(),
                event.getGuild().getId());
    }

    private void deleteMessage(@NotNull GuildMessageReceivedEvent event) {
        event.getMessage().delete().queue();
    }

    private void quarantineAuthor(@NotNull GuildMessageReceivedEvent event) {
        quarantineAuthor(event.getGuild(), event.getMember(), event.getJDA().getSelfUser());
    }

    private void quarantineAuthor(@NotNull Guild guild, @NotNull Member author,
            @NotNull SelfUser bot) {
        String reason = "User posted scam that was automatically detected";

        actionsStore.addAction(guild.getIdLong(), bot.getIdLong(), author.getIdLong(),
                ModerationAction.QUARANTINE, null, reason);

        guild
            .addRoleToMember(author,
                    ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
            .reason(reason)
            .queue();
    }

    private void reportScamMessage(@NotNull GuildMessageReceivedEvent event,
            @NotNull String reportTitle, @Nullable ActionRow confirmDialog) {
        Guild guild = event.getGuild();
        Optional<TextChannel> reportChannel = getReportChannel(guild);
        if (reportChannel.isEmpty()) {
            logger.warn(
                    "Unable to report a scam message, did not find a report channel matching the configured pattern '{}' for guild '{}'",
                    reportChannelPattern, guild.getName());
            return;
        }

        User author = event.getAuthor();
        MessageEmbed embed =
                new EmbedBuilder().setDescription(event.getMessage().getContentStripped())
                    .setTitle(reportTitle)
                    .setAuthor(author.getAsTag(), null, author.getAvatarUrl())
                    .setTimestamp(event.getMessage().getTimeCreated())
                    .setColor(AMBIENT_COLOR)
                    .setFooter(author.getId())
                    .build();
        Message message =
                new MessageBuilder().setEmbeds(embed).setActionRows(confirmDialog).build();

        reportChannel.orElseThrow().sendMessage(message).queue();
    }

    private void dmUser(@NotNull GuildMessageReceivedEvent event) {
        dmUser(event.getGuild(), event.getAuthor().getIdLong(), event.getJDA());
    }

    private void dmUser(@NotNull Guild guild, long userId, @NotNull JDA jda) {
        String dmMessage =
                """
                        Hey there, we detected that you did send scam in the server %s and therefore put you under quarantine.
                        This means you can no longer interact with anyone in the server until you have been unquarantined again.

                        If you think this was a mistake (for example, your account was hacked, but you got back control over it),
                        please contact a moderator or admin of the server.
                        """
                    .formatted(guild.getName());

        jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .queue();
    }

    private @NotNull Optional<TextChannel> getReportChannel(@NotNull Guild guild) {
        return guild.getTextChannelCache().stream().filter(isReportChannel).findAny();
    }

    private @NotNull ActionRow createConfirmDialog(@NotNull GuildMessageReceivedEvent event) {
        ComponentIdArguments args = new ComponentIdArguments(mode, event.getGuild().getIdLong(),
                event.getChannel().getIdLong(), event.getMessageIdLong(),
                event.getAuthor().getIdLong(),
                ScamHistoryStore.hashMessageContent(event.getMessage()));

        return ActionRow.of(Button.of(ButtonStyle.SUCCESS, generateComponentId(args), "Yes"),
                Button.of(ButtonStyle.DANGER, generateComponentId(args), "No"));
    }

    private @NotNull String generateComponentId(@NotNull ComponentIdArguments args) {
        return Objects.requireNonNull(componentIdGenerator)
            .generate(new ComponentId(getName(), args.toList()), Lifespan.REGULAR);
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> argsRaw) {
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
            logger.info(
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
                            "Attempted to delete scam messages, bot the channel '{}' does not exist anymore, skipping deleting messages for this channel.",
                            scamMessage.channelId());
                    return;
                }

                channel.deleteMessageById(scamMessage.messageId()).mapToResult().queue();
            });
        };

        Consumer<Throwable> onRetrieveAuthorFailure = new ErrorHandler()
            .handle(ErrorResponse.UNKNOWN_USER,
                    failure -> logger.debug(
                            "Attempted to handle scam, but user '{}' does not exist anymore.",
                            args.authorId))
            .handle(ErrorResponse.UNKNOWN_MEMBER, failure -> logger.debug(
                    "Attempted to handle scam, but user '{}' is not a member of the guild anymore.",
                    args.authorId));

        guild.retrieveMemberById(args.authorId)
            .queue(onRetrieveAuthorSuccess, onRetrieveAuthorFailure);
    }


    private record ComponentIdArguments(@NotNull ScamBlockerConfig.Mode mode, long guildId,
            long channelId, long messageId, long authorId, @NotNull String contentHash) {

        static @NotNull ComponentIdArguments fromList(@NotNull List<String> args) {
            ScamBlockerConfig.Mode mode = ScamBlockerConfig.Mode.valueOf(args.get(0));
            long guildId = Long.parseLong(args.get(1));
            long channelId = Long.parseLong(args.get(2));
            long messageId = Long.parseLong(args.get(3));
            long authorId = Long.parseLong(args.get(4));
            String contentHash = args.get(5);
            return new ComponentIdArguments(mode, guildId, channelId, messageId, authorId,
                    contentHash);
        }

        @NotNull
        List<String> toList() {
            return List.of(mode.name(), Long.toString(guildId), Long.toString(channelId),
                    Long.toString(messageId), Long.toString(authorId), contentHash);
        }
    }
}
