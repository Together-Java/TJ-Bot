package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;

import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages from suggestion channels and reacts with an up- and
 * down-vote on them to indicate to users that they can vote on the suggestion.
 */
public final class SuggestionsUpDownVoter extends MessageReceiverAdapter {
    /**
     * Creates a new listener to receive all message sent in suggestion channels.
     *
     * @param config the config to use for this
     */
    public SuggestionsUpDownVoter(@NotNull Config config) {
        super(Pattern.compile(config.getSuggestionChannelPattern()));
    }

    @Override
    public void onMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        event.getMessage().addReaction("👍").queue();
        event.getMessage().addReaction("👎").queue();
    }
}
