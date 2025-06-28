package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.QuoteBoardConfig;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Manager for the cool messages board. It appends highly-voted text messages to a separate channel
 * where members of the guild can see a list of all of them. User reacts to a message with a
 * configured emoji it then forwards this message to the configured quote board channel
 */
public final class QuoteBoardForwarder extends MessageReceiverAdapter {

    private static final Logger logger = LoggerFactory.getLogger(QuoteBoardForwarder.class);
    private final Emoji triggerReaction;
    private final Predicate<String> isQuoteBoardChannelName;
    private final QuoteBoardConfig config;

    /**
     * Constructs a new instance of QuoteBoardForwarder.
     *
     * @param config the configuration containing settings specific to the cool messages board,
     *        including the reaction emoji and the pattern to match board channel names
     */
    public QuoteBoardForwarder(Config config) {
        this.config = config.getCoolMessagesConfig();
        this.triggerReaction = Emoji.fromUnicode(this.config.reactionEmoji());

        isQuoteBoardChannelName =
                Pattern.compile(this.config.boardChannelPattern()).asMatchPredicate();
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        final MessageReaction messageReaction = event.getReaction();
        boolean isCoolEmoji = messageReaction.getEmoji().equals(triggerReaction);
        long guildId = event.getGuild().getIdLong();

        if (hasAlreadyForwardedMessage(event.getJDA(), messageReaction)) {
            return;
        }

        final int reactionsCount = (int) messageReaction.retrieveUsers().stream().count();
        if (isCoolEmoji && reactionsCount >= config.minimumReactions()) {
            Optional<TextChannel> boardChannel = findQuoteBoardChannel(event.getJDA(), guildId);

            if (boardChannel.isEmpty()) {
                logger.warn(
                        "Could not find board channel with pattern '{}' in server with ID '{}'. Skipping reaction handling...",
                        this.config.boardChannelPattern(), guildId);
                return;
            }

            event.retrieveMessage()
                .queue(message -> markAsProcessed(message).flatMap(v -> message
                    .forwardTo(boardChannel.orElseThrow())).queue(), e -> logger.warn(
                            "Unknown error while attempting to retrieve and forward message for quote-board, message is ignored.",
                            e));
        }
    }

    private RestAction<Void> markAsProcessed(Message message) {
        return message.addReaction(triggerReaction);
    }

    /**
     * Gets the board text channel where the quotes go to, wrapped in an optional.
     *
     * @param jda the JDA
     * @param guildId the guild ID
     * @return the board text channel
     */
    private Optional<TextChannel> findQuoteBoardChannel(JDA jda, long guildId) {
        return jda.getGuildById(guildId)
            .getTextChannelCache()
            .stream()
            .filter(channel -> isQuoteBoardChannelName.test(channel.getName()))
            .findAny();
    }

    /**
     * Inserts a message to the specified text channel
     *
     * @return a {@link MessageCreateAction} of the call to make
     */

    /**
     * Checks a {@link MessageReaction} to see if the bot has reacted to it.
     */
    private boolean hasAlreadyForwardedMessage(JDA jda, MessageReaction messageReaction) {
        if (!triggerReaction.equals(messageReaction.getEmoji())) {
            return false;
        }

        return messageReaction.retrieveUsers()
            .parallelStream()
            .anyMatch(user -> jda.getSelfUser().getIdLong() == user.getIdLong());
    }
}
