package org.togetherjava.tjbot.commands.moderation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.BotCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.MessageContextCommand;
import org.togetherjava.tjbot.commands.utils.DiscordClientAction;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.config.Config;

import java.awt.*;
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
        String reportedMessageChannel = event.getTarget().getChannel().getId();
        String authorName = event.getTarget().getAuthor().getName();
        String authorAvatarURL = event.getTarget().getAuthor().getAvatarUrl();
        String authorID = event.getTarget().getAuthor().getId();

        TextInput body = TextInput
            .create(REPORT_REASON_INPUT_ID, "Anonymous report to the moderators",
                    TextInputStyle.PARAGRAPH)
            .setPlaceholder("What is wrong with the message, why do you want to report it?")
            .setRequiredRange(3, 200)
            .build();

        String reportModalComponentID =
                generateComponentId(reportedMessage, reportedMessageID, reportedMessageChannel,
                        reportedMessageTimestamp, authorName, authorAvatarURL, authorID);
        Modal reportModal = Modal.create(reportModalComponentID, "Report this to a moderator")
            .addActionRow(body)
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
        long guildID = Objects.requireNonNull(event.getGuild(), "Could not retrieve the guildId.")
            .getIdLong();
        Optional<TextChannel> modMailAuditLog = event.getJDA()
            .getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
        if (modMailAuditLog.isEmpty()) {
            event
                .reply("Sorry, there was an issue sending your report to the moderators. We are "
                        + "investigating.")
                .setEphemeral(true)
                .queue();
            logger.warn(
                    "Cannot find the designated modmail channel in server by id {} with the pattern {}",
                    guildID, configModMailChannelPattern);
        }
        return modMailAuditLog;
    }

    private MessageCreateAction createModMessage(String reportReason, ReportMessage reportMessage,
            Guild guild, TextChannel modMailAuditLog) {

        MessageEmbed reportedMessageEmbed = new EmbedBuilder().setTitle("Report")
            .setDescription(MessageUtils.abbreviate(reportMessage.reportedMessage,
                    MessageEmbed.DESCRIPTION_MAX_LENGTH))
            .setAuthor(reportMessage.reportedMessageAuthorName, null,
                    reportMessage.reportedMessageAuthorAvatarUrl)
            .setTimestamp(reportMessage.reportedMessageTimestamp)
            .setColor(AMBIENT_COLOR)
            .build();

        MessageEmbed reportReasonEmbed = new EmbedBuilder().setTitle("Reason")
            .setDescription(reportReason)
            .setColor(AMBIENT_COLOR)
            .build();
        return modMailAuditLog.sendMessageEmbeds(reportedMessageEmbed, reportReasonEmbed)
            .addActionRow(DiscordClientAction.Channels.GUILD_CHANNEL_MESSAGE.asLinkButton(
                    "Reported Message", guild.getId(), reportMessage.reportedMessageChannelID,
                    reportMessage.reportedMessageID));
    }

    private void sendModMessage(ModalInteractionEvent event, List<String> args,
            TextChannel modMailAuditLog) {
        String reportedMessage = args.get(0);
        String reportedMessageID = args.get(1);
        String reportedMessageChannelID = args.get(2);
        Instant reportedMessageTimestamp = Instant.parse(args.get(3));
        String reportedMessageAuthorName = args.get(4);
        String reportedMessageAuthorAvatarUrl = args.get(5);

        ReportMessage reportMessage = new ReportMessage(reportedMessage, reportedMessageID,
                reportedMessageChannelID, reportedMessageTimestamp, reportedMessageAuthorName,
                reportedMessageAuthorAvatarUrl);

        Guild guild = event.getGuild();
        event.deferReply().setEphemeral(true).queue();

        InteractionHook hook = event.getHook();
        String reportReason = event.getValue(REPORT_REASON_INPUT_ID).getAsString();

        createModMessage(reportReason, reportMessage, guild, modMailAuditLog).mapToResult()
            .map(this::createUserReply)
            .flatMap(hook::editOriginal)
            .queue();
    }

    record ReportMessage(String reportedMessage, String reportedMessageID,
            String reportedMessageChannelID, Instant reportedMessageTimestamp,
            String reportedMessageAuthorName, String reportedMessageAuthorAvatarUrl) {
    }


    private String createUserReply(Result<Message> result) {
        if (result.isFailure()) {
            logger.warn("Unable to forward a message report to modmail channel.",
                    result.getFailure());
            return "Sorry, there was an issue sending your report to the moderators. We are investigating.";
        }
        return "Thank you for reporting this message. A moderator will take care of the matter as soon as possible.";
    }

}
