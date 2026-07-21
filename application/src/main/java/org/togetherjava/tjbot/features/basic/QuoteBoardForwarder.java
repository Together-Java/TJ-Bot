package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.QuoteBoardConfig;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;
import org.togetherjava.tjbot.features.Routine;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Listens for reaction-add events and turns popular messages into "quotes".
 * <p>
 * When someone reacts to a message with the configured emoji, the listener counts how many users
 * have used that same emoji. If the total meets or exceeds the minimum threshold and the bot has
 * not processed the message before, it copies (forwards) the message to the first text channel
 * whose name matches the configured quote-board pattern, then reacts to the original message itself
 * to mark it as handled (and to not let people spam react a message and give a way to the bot to
 * know that a message has been quoted before).
 * <p>
 * Key points: - Trigger emoji, minimum vote count and quote-board channel pattern are supplied via
 * {@code QuoteBoardConfig}.
 */
public final class QuoteBoardForwarder extends MessageReceiverAdapter implements Routine {

    private static final Logger logger = LoggerFactory.getLogger(QuoteBoardForwarder.class);

    private record ReactedMessage(long guildId, long channelId, long messageId) {
    }

    private final Emoji messageForwardedEmojiMarker;
    private final Predicate<String> isQuoteBoardChannelName;
    private final QuoteBoardConfig config;

    private final Object reactedMessagesLock = new Object();
    private Set<ReactedMessage> reactedMessages = new HashSet<>();

    /**
     * Constructs a new instance of QuoteBoardForwarder.
     *
     * @param config the configuration containing settings specific to the cool messages board,
     *        including the reaction emoji and the pattern to match board channel names
     */
    public QuoteBoardForwarder(Config config) {
        this.config = config.getQuoteBoardConfig();
        this.messageForwardedEmojiMarker = Emoji.fromUnicode(this.config.botEmoji());

        this.isQuoteBoardChannelName = Pattern.compile(this.config.channel()).asMatchPredicate();
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        logger.debug("Received MessageReactionAddEvent: messageId={}, channelId={}, userId={}",
                event.getMessageId(), event.getChannel().getId(), event.getUserId());

        if (!config.allowChannels().contains(event.getChannel().getName())) {
            logger.debug("Skipping as reaction occurred in non-whitelisted channel");
            return;
        }

        if (event.getChannelType() != ChannelType.TEXT) {
            logger.debug("Skipping reaction as only text-channels are supported");
            return;
        }

        synchronized (reactedMessagesLock) {
            reactedMessages.add(new ReactedMessage(event.getGuild().getIdLong(),
                    event.getChannel().getIdLong(), event.getMessageIdLong()));
        }
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_DELAY, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(JDA jda) {
        Set<ReactedMessage> messagesToProcess;
        synchronized (reactedMessagesLock) {
            messagesToProcess = reactedMessages;
            reactedMessages = new HashSet<>();
        }

        messagesToProcess.forEach(message -> {
            try {
                processMessage(jda, message);
            } catch (Exception e) {
                logger.warn(
                        "Failed to process message ({}) for potentially forwarding it to the quote-board.",
                        message, e);
            }
        });
    }

    private void processMessage(JDA jda, ReactedMessage reactedMessage) {
        Optional<TextChannel> boardChannelOptional =
                findQuoteBoardChannel(jda, reactedMessage.guildId);
        if (boardChannelOptional.isEmpty()) {
            logger.warn(
                    "Could not find board channel with pattern '{}' in server with ID '{}'. Skipping reaction handling...",
                    config.channel(), reactedMessage.guildId);
            return;
        }

        TextChannel boardChannel = boardChannelOptional.orElseThrow();
        if (boardChannel.getIdLong() == reactedMessage.channelId) {
            logger.debug(
                    "Someone tried to react with the react emoji to the quotes channel, ignoring.");
            return;
        }

        jda.getGuildById(reactedMessage.guildId)
            .getTextChannelById(reactedMessage.channelId)
            .retrieveMessageById(reactedMessage.messageId)
            .queue(message -> {
                if (hasAlreadyForwardedMessage(message)) {
                    logger.debug("Message has already been forwarded by the bot. Skipping.");
                    return;
                }

                double emojiScore = calculateMessageScore(message.getReactions());
                if (emojiScore < config.minimumScoreToTrigger()) {
                    return;
                }

                forwardMessage(message, boardChannel);
            });
    }

    private Optional<TextChannel> findQuoteBoardChannel(JDA jda, long guildId) {
        Guild guild = jda.getGuildById(guildId);

        if (guild == null) {
            throw new IllegalStateException(
                    String.format("Guild with ID '%d' not found.", guildId));
        }

        List<TextChannel> matchingChannels = guild.getTextChannelCache()
            .stream()
            .filter(channel -> isQuoteBoardChannelName.test(channel.getName()))
            .toList();

        if (matchingChannels.size() > 1) {
            logger.warn(
                    "Multiple quote board channels found matching pattern '{}' in guild with ID '{}'. Selecting the first one anyway.",
                    this.config.channel(), guildId);
        }

        return matchingChannels.stream().findFirst();
    }

    private boolean hasAlreadyForwardedMessage(Message message) {
        return message.getReactions()
            .stream()
            .filter(reaction -> messageForwardedEmojiMarker.equals(reaction.getEmoji()))
            .anyMatch(MessageReaction::isSelf);
    }

    private double calculateMessageScore(List<MessageReaction> reactions) {
        return reactions.stream()
            .mapToDouble(reaction -> reaction.getCount() * getEmojiScore(reaction.getEmoji()))
            .sum();
    }

    private float getEmojiScore(Emoji emoji) {
        float defaultScore = config.defaultEmojiScore();
        String reactionCode = emoji.getAsReactionCode();

        return config.emojiScores().getOrDefault(reactionCode, defaultScore);
    }

    private void forwardMessage(Message message, MessageChannel boardChannel) {
        logger.debug("Attempting to forward message to quote board channel: {}",
                boardChannel.getName());

        markMessageAsForwarded(message).flatMap(_ -> message.forwardTo(boardChannel))
            .queue(_ -> logger.debug("Message forwarded to quote board channel: {}", boardChannel
                .getName()), e -> logger.warn(
                        "Unknown error while attempting to retrieve and forward message for quote-board, message is ignored.",
                        e));
    }

    private RestAction<Void> markMessageAsForwarded(Message message) {
        return message.addReaction(messageForwardedEmojiMarker);
    }
}
