package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A class containing helper methods required by the free package command.
 */
enum FreeUtil {
    ;
    private static final Logger logger = LoggerFactory.getLogger(FreeUtil.class);

    /**
     * Helper method to easily send ephemeral messages to users.
     * 
     * @param interaction The event or hook that this message is responding to
     * @param message The text to be display for the user to read.
     */
    public static void sendErrorMessage(@NotNull IReplyCallback interaction,
            @NotNull String message) {
        interaction.reply(message).setEphemeral(true).queue();
    }

    /**
     * Method that provides the message history of a {@link TextChannel}.
     * <p>
     * </p>
     * This method attempts to retrieve the message history, and logs any problems that occur in the
     * attempt.
     *
     * @param channel the channel from which the history is required.
     * @param limit the number of messages to retrieve.
     * @return the requested message history or empty if unable to.
     */
    public static @NotNull Optional<List<Message>> getChannelHistory(@NotNull TextChannel channel,
            final int limit) {
        return channel.getHistory().retrievePast(limit).mapToResult().map(listResult -> {
            if (listResult.isFailure()) {
                logger.error("Failed to retrieve messages from %s because of:"
                    .formatted(channel.getAsMention()), listResult.getFailure());
                return Optional.<List<Message>>empty();
            }
            return Optional.of(listResult.get());
        }).complete();
    }

    /**
     * Method that provides the id of the latest message in a {@link TextChannel}.
     * <p>
     * This method tests for problems with retrieving the id like the latest message was deleted and
     * the channel history being empty (or network trouble), etc.
     *
     * @param channel the channel from which the latest message is required.
     * @return the id of the latest message or empty if it could not be retrieved.
     */
    public static @NotNull OptionalLong getLastMessageId(@NotNull TextChannel channel) {
        // black magic to convert Optional<Long> into OptionalLong because Optional does not have
        // .mapToLong
        return getChannelHistory(channel, 1).stream()
            .flatMap(List::stream)
            .mapToLong(Message::getIdLong)
            .findFirst();
    }
}
