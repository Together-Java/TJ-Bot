package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.SuggestionsConfig;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages from suggestion channels and reacts with an up- and
 * down-vote on them to indicate to users that they can vote on the suggestion.
 */
public final class SuggestionsUpDownVoter extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SuggestionsUpDownVoter.class);
    private static final int TITLE_MAX_LENGTH = 60;
    private static final Emoji FALLBACK_UP_VOTE = Emoji.fromUnicode("👍");
    private static final Emoji FALLBACK_DOWN_VOTE = Emoji.fromUnicode("👎");

    private final SuggestionsConfig config;

    /**
     * Creates a new listener to receive all message sent in suggestion channels.
     *
     * @param config the config to use for this
     */
    public SuggestionsUpDownVoter(Config config) {
        super(Pattern.compile(config.getSuggestions().getChannelPattern()));

        this.config = config.getSuggestions();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage() || !event.isFromGuild()) {
            return;
        }

        Guild guild = event.getGuild();
        Message message = event.getMessage();

        createThread(message);
        reactWith(config.getUpVoteEmoteName(), FALLBACK_UP_VOTE, guild, message);
        reactWith(config.getDownVoteEmoteName(), FALLBACK_DOWN_VOTE, guild, message);
    }

    private static void createThread(Message message) {
        String title = message.getContentRaw();

        if (title.length() >= TITLE_MAX_LENGTH) {
            int lastWordEnd = title.lastIndexOf(' ', TITLE_MAX_LENGTH);

            if (lastWordEnd == -1) {
                lastWordEnd = TITLE_MAX_LENGTH;
            }

            title = title.substring(0, lastWordEnd);
        }

        message.createThreadChannel(title).queue();
    }

    private static void reactWith(String emojiName, Emoji fallbackEmoji, Guild guild,
            Message message) {
        getEmojiByName(emojiName, guild).map(message::addReaction).orElseGet(() -> {
            logger.warn(
                    "Unable to vote on a suggestion with the configured emoji ('{}'), using fallback instead.",
                    emojiName);
            return message.addReaction(fallbackEmoji);
        }).queue(ignored -> {
        }, exception -> {
            if (exception instanceof ErrorResponseException responseException
                    && responseException.getErrorResponse() == ErrorResponse.REACTION_BLOCKED) {
                // User blocked the bot, hence the bot can not add reactions to their messages.
                // Nothing we can do here.
                return;
            }

            logger.error("Attempted to react to a suggestion, but failed", exception);
        });
    }

    private static Optional<RichCustomEmoji> getEmojiByName(String name, Guild guild) {
        return guild.getEmojisByName(name, false).stream().findAny();
    }
}
