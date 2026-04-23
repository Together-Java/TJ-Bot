package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.QuoteBoardConfig;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.util.List;
import java.util.Optional;
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
public final class QuoteBoardForwarder extends MessageReceiverAdapter {

    private static final Logger logger = LoggerFactory.getLogger(QuoteBoardForwarder.class);
    private final Emoji botEmoji;
    private final Predicate<String> isQuoteBoardChannelName;
    private final QuoteBoardConfig config;

    /**
     * Constructs a new instance of QuoteBoardForwarder.
     *
     * @param config the configuration containing settings specific to the cool messages board,
     *        including the reaction emoji and the pattern to match board channel names
     */
    public QuoteBoardForwarder(Config config) {
        this.config = config.getQuoteBoardConfig();
        this.botEmoji = Emoji.fromUnicode(this.config.botEmoji());

        this.isQuoteBoardChannelName = Pattern.compile(this.config.channel()).asMatchPredicate();
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        logger.debug("Received MessageReactionAddEvent: messageId={}, channelId={}, userId={}",
                event.getMessageId(), event.getChannel().getId(), event.getUserId());

        final MessageReaction messageReaction = event.getReaction();

        if (hasAlreadyForwardedMessage(event.getJDA(), messageReaction)) {
            logger.debug("Message has already been forwarded by the bot. Skipping.");
            return;
        }

        final long guildId = event.getGuild().getIdLong();

        Optional<TextChannel> boardChannelOptional = findQuoteBoardChannel(event.getJDA(), guildId);

        if (boardChannelOptional.isEmpty()) {
            logger.warn(
                    "Could not find board channel with pattern '{}' in server with ID '{}'. Skipping reaction handling...",
                    this.config.channel(), guildId);
            return;
        }

        TextChannel boardChannel = boardChannelOptional.orElseThrow();

        if (boardChannel.getId().equals(event.getChannel().getId())) {
            logger.debug("Someone tried to react with the react emoji to the quotes channel.");
            return;
        }

        event.retrieveMessage().queue(message -> {
            float emojiScore = calculateMessageScore(message.getReactions());

            if (emojiScore < config.minimumScoreToTrigger()) {
                return;
            }

            logger.debug("Attempting to forward message to quote board channel: {}",
                    boardChannel.getName());

            markAsProcessed(message).flatMap(_ -> message.forwardTo(boardChannel))
                .queue(_ -> logger.debug("Message forwarded to quote board channel: {}",
                        boardChannel.getName()),
                        e -> logger.warn(
                                "Unknown error while attempting to retrieve and forward message for quote-board, message is ignored.",
                                e));
        });
    }

    private RestAction<Void> markAsProcessed(Message message) {
        return message.addReaction(botEmoji);
    }

    /**
     * Gets the board text channel where the quotes go to, wrapped in an optional.
     *
     * @param jda the JDA
     * @param guildId the guild ID
     * @return the board text channel
     */
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

    /**
     * Checks a {@link MessageReaction} to see if the bot has reacted to it.
     */
    private boolean hasAlreadyForwardedMessage(JDA jda, MessageReaction messageReaction) {
        if (!botEmoji.equals(messageReaction.getEmoji())) {
            return false;
        }

        return messageReaction.retrieveUsers()
            .parallelStream()
            .anyMatch(user -> jda.getSelfUser().getIdLong() == user.getIdLong());
    }

    private float calculateMessageScore(List<MessageReaction> reactions) {
        return (float) reactions.stream()
            .mapToDouble(reaction -> reaction.getCount() * getEmojiScore(reaction.getEmoji()))
            .sum();
    }

    private float getEmojiScore(EmojiUnion emoji) {
        float defaultScore = config.defaultEmojiScore();
        String reactionCode = emoji.getAsReactionCode();

        return config.emojiScores().getOrDefault(reactionCode, defaultScore);
    }
}
