package org.togetherjava.tjbot.commands.moderation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.BotCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.MessageContextCommand;
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
 * Implements the /report command, which allows users to contact a moderator within the server which
 * forwards messages to moderators in a dedicated channel given by
 * {@link Config#getModMailChannelPattern()}.
 *
 * The /report command would give us necessary information about the offending user, and specific
 * message that was offensive.
 */
public final class ReportCommand extends BotCommandAdapter implements MessageContextCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReportCommand.class);
    private static final String COMMAND_NAME = "report";
    private static final String REPORT_REASON = "reportReason";
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

        if (handleIsOnCooldown(userID, event)) {
            return;
        }
        authorToLastReportInvocation.put(userID, Instant.now());

        String reportedMessage = event.getTarget().getContentRaw();
        String reportedMessageUrl = event.getTarget().getJumpUrl();

        TextInput body = TextInput
            .create(REPORT_REASON, "Anonymous report to the moderators", TextInputStyle.PARAGRAPH)
            .setPlaceholder("What is wrong with the message, why do you want to report it?")
            .setRequiredRange(3, 200)
            .build();

        Modal modal =
                Modal.create(generateComponentId(reportedMessage, reportedMessageUrl), "Report")
                    .addActionRow(body)
                    .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        String reportedMessage = args.get(0);
        String reportedMessageUrl = args.get(1);

        long guildID = Objects.requireNonNull(event.getGuild(), "Could not retrieve the guildId.")
            .getIdLong();
        Optional<TextChannel> modMailAuditLog = getModMailChannel(event.getJDA(), guildID);
        if (modMailAuditLog.isEmpty()) {
            logger.warn(
                    "Cannot find the designated modmail channel in server by id {} with the pattern {}",
                    guildID, configModMailChannelPattern);
            return;
        }
        InteractionHook hook = event.getHook();
        event.deferReply().setEphemeral(true).queue();
        String modalMessage = event.getValue(REPORT_REASON).getAsString();
        long userID = event.getUser().getIdLong();
        MessageCreateAction modMessage = createModMessage(modalMessage, userID, reportedMessage,
                reportedMessageUrl, modMailAuditLog.orElseThrow());

        sendModMessage(hook, modMessage);
    }

    private boolean handleIsOnCooldown(long userID, MessageContextInteractionEvent event) {
        if (!isAuthorOnCooldown(userID)) {
            return false;
        }
        event.reply("Can only be used once per %s minutes.".formatted(COOLDOWN_DURATION_VALUE))
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

    private Optional<TextChannel> getModMailChannel(JDA jda, long guildID) {
        return jda.getGuildById(guildID)
            .getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    private MessageCreateAction createModMessage(String modalMessage, long userID,
            String reportedMessage, String reportedMessageUrl, TextChannel modMailAuditLog) {
        MessageEmbed embed = new EmbedBuilder().setTitle("Report")
            .setDescription(("""
                    Reported Message: **%s**
                    [Reported Message URL](%s)
                    Modal Message: **%s**""").formatted(reportedMessage, reportedMessageUrl,
                    modalMessage))
            .setAuthor("Author ID: %s".formatted(userID))
            .setColor(AMBIENT_COLOR)
            .setTimestamp(Instant.now())
            .build();

        return modMailAuditLog.sendMessageEmbeds(embed);
    }

    private void sendModMessage(InteractionHook hook, MessageCreateAction message) {
        message.mapToResult().map(result -> {
            if (result.isSuccess()) {
                return "Thank you for reporting this message. A moderator will take care of the matter as soon as possible.";
            }
            logger.warn("Unable to forward a message report to modmail channel.");
            return "Sorry, there was an issue sending your report to the moderators. We are investigating.";
        }).flatMap(hook::editOriginal).queue();
    }

}
