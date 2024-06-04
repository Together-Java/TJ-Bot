package org.togetherjava.tjbot.features.moderation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Implements the /report command, which allows users to report a selected offensive message from
 * another user. The message is then forwarded to moderators in a dedicated channel given by
 * {@link Config#getModMailChannelPattern()}.
 */
public final class ReportCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final Logger logger = LoggerFactory.getLogger(ReportCommand.class);
    private static final String COMMAND_NAME = "report";
    private static final String REPORT_REASON_INPUT_ID = "reportReason";
    private static final int COOLDOWN_DURATION_VALUE = 3;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final Color AMBIENT_COLOR = Color.BLACK;
    private final Cache<Long, Instant> authorToLastReportInvocation = createCooldownCache();
    private final Predicate<String> modMailChannelNamePredicate;
    private final Predicate<String> configModGroupPattern;
    private final String configModMailChannelPattern;

    /**
     * Creates a new instance.
     *
     * @param config to get the channel to forward reports to
     */
    public ReportCommand(Config config) {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);

        modMailChannelNamePredicate =
                Pattern.compile(config.getModMailChannelPattern()).asMatchPredicate();

        configModMailChannelPattern = config.getModMailChannelPattern();

        configModGroupPattern =
                Pattern.compile(config.getHeavyModerationRolePattern()).asMatchPredicate();
    }

    private Cache<Long, Instant> createCooldownCache() {
        return Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
            .build();
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        long userID = event.getUser().getIdLong();
        String reportedMessageTimestamp = event.getTarget().getTimeCreated().toInstant().toString();

        if (handleIsOnCooldown(event)) {
            return;
        }
        authorToLastReportInvocation.put(userID, Instant.now());

        String reportedMessage = event.getTarget().getContentRaw();
        String reportedMessageID = event.getTarget().getId();
        String reportedMessageJumpUrl = event.getTarget().getJumpUrl();
        String reportedMessageChannel = event.getTarget().getChannel().getId();
        String reportedAuthorName = event.getTarget().getAuthor().getName();
        String reportedAuthorAvatarUrl = event.getTarget().getAuthor().getEffectiveAvatarUrl();
        String reportedAuthorID = event.getTarget().getAuthor().getId();

        TextInput modalTextInput = TextInput
            .create(REPORT_REASON_INPUT_ID, "Anonymous report to the moderators",
                    TextInputStyle.PARAGRAPH)
            .setPlaceholder("Why do you want to report this message?")
            .setRequiredRange(3, 200)
            .build();

        String reportModalComponentID = generateComponentId(reportedMessage, reportedMessageID,
                reportedMessageJumpUrl, reportedMessageChannel, reportedMessageTimestamp,
                reportedAuthorName, reportedAuthorAvatarUrl, reportedAuthorID);
        Modal reportModal = Modal.create(reportModalComponentID, "Report this to a moderator")
            .addActionRow(modalTextInput)
            .build();

        event.replyModal(reportModal).queue();
    }

    private boolean handleIsOnCooldown(MessageContextInteractionEvent event) {
        if (!isAuthorOnCooldown(event.getUser().getIdLong())) {
            return false;
        }
        event
            .reply("You can only report a message once per %s minutes."
                .formatted(COOLDOWN_DURATION_VALUE))
            .setEphemeral(true)
            .queue();
        return true;
    }

    private boolean isAuthorOnCooldown(long userId) {
        return Optional.ofNullable(authorToLastReportInvocation.getIfPresent(userId))
            .map(sinceCommandInvoked -> sinceCommandInvoked.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        Optional<TextChannel> modMailAuditLog = handleRequireModMailChannel(event);

        if (modMailAuditLog.isEmpty()) {
            return;
        }

        sendModMessage(event, args, modMailAuditLog.orElseThrow());
    }

    private Optional<TextChannel> handleRequireModMailChannel(ModalInteractionEvent event) {
        long guildID = Objects
            .requireNonNull(event.getGuild(),
                    "Guild is null for ModalInteractionEvent in ReportCommand.")
            .getIdLong();
        Optional<TextChannel> modMailAuditLog = event.getJDA()
            .getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
        if (modMailAuditLog.isEmpty()) {
            event.reply(
                    "Sorry, there was an issue sending your report to the moderators. We are investigating.")
                .setEphemeral(true)
                .queue();
            logger.warn(
                    "Cannot find the designated modmail channel in server by id {} with the pattern {}",
                    guildID, configModMailChannelPattern);
        }
        return modMailAuditLog;
    }

    private MessageCreateAction createModMessage(String reportReason,
            ReportedMessage reportedMessage, Guild guild, TextChannel modMailAuditLog) {
        MessageEmbed reportedMessageEmbed = new EmbedBuilder().setTitle("Report")
            .setDescription(MessageUtils.abbreviate(reportedMessage.content,
                    MessageEmbed.DESCRIPTION_MAX_LENGTH))
            .setAuthor(reportedMessage.authorName, null, reportedMessage.authorAvatarUrl)
            .setTimestamp(reportedMessage.timestamp)
            .setColor(AMBIENT_COLOR)
            .build();

        MessageEmbed reportReasonEmbed = new EmbedBuilder().setTitle("Reason")
            .setDescription(reportReason)
            .setColor(AMBIENT_COLOR)
            .build();

        MessageCreateAction message =
                modMailAuditLog.sendMessageEmbeds(reportedMessageEmbed, reportReasonEmbed)
                    .addActionRow(Button.link(reportedMessage.jumpUrl, "Go to message"));

        Optional<Role> moderatorRole = guild.getRoles()
            .stream()
            .filter(role -> configModGroupPattern.test(role.getName()))
            .findFirst();

        moderatorRole.ifPresent(role -> message.setContent(role.getAsMention()));

        return message;
    }

    private void sendModMessage(ModalInteractionEvent event, List<String> args,
            TextChannel modMailAuditLog) {
        Guild guild = event.getGuild();
        event.deferReply().setEphemeral(true).queue();

        InteractionHook hook = event.getHook();
        String reportReason = event.getValue(REPORT_REASON_INPUT_ID).getAsString();

        ReportedMessage reportedMessage = ReportedMessage.ofArgs(args);

        createModMessage(reportReason, reportedMessage, guild, modMailAuditLog).mapToResult()
            .map(ReportCommand::createUserReply)
            .flatMap(hook::editOriginal)
            .queue();
    }

    private static String createUserReply(Result<Message> result) {
        if (result.isFailure()) {
            logger.warn("Unable to forward a message report to modmail channel.",
                    result.getFailure());
            return "Sorry, there was an issue sending your report to the moderators. We are investigating.";
        }
        return "Thank you for reporting this message. A moderator will take care of the matter as soon as possible.";
    }

    private record ReportedMessage(String content, String id, String jumpUrl, String channelID,
            Instant timestamp, String authorName, String authorAvatarUrl) {
        static ReportedMessage ofArgs(List<String> args) {
            String content = args.getFirst();
            String id = args.get(1);
            String jumpUrl = args.get(2);
            String channelID = args.get(3);
            Instant timestamp = Instant.parse(args.get(4));
            String authorName = args.get(5);
            String authorAvatarUrl = args.get(6);
            return new ReportedMessage(content, id, jumpUrl, channelID, timestamp, authorName,
                    authorAvatarUrl);
        }
    }
}
