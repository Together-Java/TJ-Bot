package org.togetherjava.tjbot.commands.moderation.report;

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
 * {@link Config#getModMailChannelPattern()} ()}.
 */

public final class ReportCommand extends BotCommandAdapter implements MessageContextCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReportCommand.class);
    private static final String COMMAND_NAME = "report";
    private static final String MESSAGE_INPUT = "message";
    private static final int COOLDOWN_DURATION_VALUE = 3;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final Color AMBIENT_COLOR = Color.BLACK;
    private final Cache<Long, Instant> authorToLastModMailInvocation = createCooldownCache();
    private final Predicate<String> modMailChannelNamePredicate;
    private final String configModMailChannelPattern;
    private String reportedMessage;
    private String reportedMessageUrl;

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
        reportedMessage = event.getTarget().getContentRaw();
        reportedMessageUrl = event.getTarget().getJumpUrl();

        TextInput body = TextInput.create(MESSAGE_INPUT, "Message", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Reason for reporting")
            .setRequiredRange(10, 200)
            .build();

        Modal modal = Modal.create(generateComponentId(), "Report").addActionRow(body).build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        long userId = event.getUser().getIdLong();

        if (handleIsOnCooldown(userId, event)) {
            return;
        }
        authorToLastModMailInvocation.put(userId, Instant.now());
        event.deferReply().setEphemeral(true).queue();

        long guildId = Objects.requireNonNull(event.getGuild(), "Could not retrieve the guildId.")
            .getIdLong();
        Optional<TextChannel> modMailAuditLog = getModMailChannel(event.getJDA(), guildId);
        if (modMailAuditLog.isEmpty()) {
            logger.warn(
                    "Cannot find the designated modmail channel in server by id {} with the pattern {}",
                    guildId, configModMailChannelPattern);
            return;
        }
        String modalMessage = event.getValue(MESSAGE_INPUT).getAsString();
        MessageCreateAction message =
                createModMessage(modalMessage, userId, modMailAuditLog.orElseThrow());

        sendMessage(event, message);

        event.getHook().editOriginal("Thank you for your feedback!").queue();

    }

    private boolean handleIsOnCooldown(long userId, ModalInteractionEvent event) {
        if (!isChannelOnCooldown(userId)) {
            return false;
        }
        event.reply("Can only be used once per %s minutes.".formatted(COOLDOWN_DURATION_VALUE))
            .setEphemeral(true)
            .queue();
        return true;
    }

    private boolean isChannelOnCooldown(long userId) {
        return Optional.ofNullable(authorToLastModMailInvocation.getIfPresent(userId))
            .map(sinceCommandInvoked -> sinceCommandInvoked.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

    private Optional<TextChannel> getModMailChannel(JDA jda, long guildId) {
        return jda.getGuildById(guildId)
            .getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    private MessageCreateAction createModMessage(String modalMessage, long userID,
            TextChannel modMailAuditLog) {
        return modMailAuditLog.sendMessageEmbeds(createReportMessage(modalMessage, userID));
    }

    private void sendMessage(ModalInteractionEvent event, MessageCreateAction message) {
        InteractionHook hook = event.getHook();
        message.mapToResult().map(result -> {
            if (result.isSuccess()) {
                return "Your message has been forwarded, thanks.";
            }
            logger.warn("There was an issue with forwarding users message.");
            return "There was an issue forwarding your message, sorry. We are investigating.";
        }).flatMap(hook::editOriginal).queue();
    }

    private MessageEmbed createReportMessage(String modalMessage, long userId) {
        return new EmbedBuilder().setTitle("Report")
            .setDescription(("""
                    Reported Message: **%s**
                    [Reported Message URL](%s)
                    Modal Message: **%s**""").formatted(reportedMessage, reportedMessageUrl,
                    modalMessage))
            .setAuthor("Author ID: %s".formatted(userId))
            .setColor(AMBIENT_COLOR)
            .setTimestamp(Instant.now())
            .build();
    }
}
