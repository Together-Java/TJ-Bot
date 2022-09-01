package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.SuggestionsConfig;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages from suggestion channels and reacts with an up- and
 * down-vote on them to indicate to users that they can vote on the suggestion.
 */
public final class SuggestionsUpDownVoter extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SuggestionsUpDownVoter.class);
    private static final int TITLE_MAX_LENGTH = 60;
    private static final String FALLBACK_UP_VOTE = "👍";
    private static final String FALLBACK_DOWN_VOTE = "👎";

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

    private static void reactWith(String emoteName, String fallbackUnicodeEmote, Guild guild,
            Message message) {
        getEmoteByName(emoteName, guild).map(message::addReaction).orElseGet(() -> {
            logger.warn(
                    "Unable to vote on a suggestion with the configured emote ('{}'), using fallback instead.",
                    emoteName);
            return message.addReaction(fallbackUnicodeEmote);
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

    private static Optional<Emote> getEmoteByName(String name, Guild guild) {
        return guild.getEmotesByName(name, false).stream().findAny();
    }
}
