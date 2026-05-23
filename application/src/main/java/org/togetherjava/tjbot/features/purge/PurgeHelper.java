package org.togetherjava.tjbot.features.purge;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared helpers used by the {@link PurgeCommand} family. Centralises the confirmation dialog and
 * the recursive history-pagination + bulk-delete loop so each variant only owns its own input
 * handling.
 */
final class PurgeHelper {
    private static final Logger logger = LoggerFactory.getLogger(PurgeHelper.class);

    static final int BATCH_SIZE = 100;
    static final String CONFIRM_ACTION = "confirm";
    static final String CANCEL_ACTION = "cancel";

    private PurgeHelper() {}

    /**
     * Replies to {@code event} with an ephemeral confirmation embed and a Confirm (danger) / Cancel
     * (secondary) button row.
     */
    static void sendConfirmationDialog(IReplyCallback event, String title, String description,
            String confirmComponentId, String cancelComponentId) {
        EmbedBuilder embed =
                new EmbedBuilder().setTitle(title).setDescription(description).setColor(Color.RED);

        event.replyEmbeds(embed.build())
            .setEphemeral(true)
            .addActionRow(Button.danger(confirmComponentId, "Confirm purge"),
                    Button.secondary(cancelComponentId, "Cancel"))
            .queue();
    }

    /**
     * Edits the originating message to indicate the purge was cancelled and removes the buttons.
     */
    static void handleCancel(ButtonInteractionEvent event) {
        event.editMessage("Purge cancelled.").setEmbeds().setComponents().queue();
    }

    /**
     * Recursively deletes messages newer than {@code messageId} in {@code channel} that satisfy
     * {@code filter}, up to {@code remaining} matches.
     * <p>
     * Each call fetches a full {@link #BATCH_SIZE} batch via
     * {@link MessageChannel#getHistoryAfter(String, int)}, filters it, deletes the matches with
     * {@link MessageChannel#purgeMessages(List)}, then recurses using the newest message of the
     * fetched batch as the next anchor. {@code onComplete} fires exactly once with the cumulative
     * count of matches submitted for deletion. Fetch failures are logged and treated as the end of
     * the channel.
     *
     * @param channel the channel to scan and purge
     * @param messageId snowflake id of the anchor (exclusive lower bound)
     * @param remaining maximum further matches that may still be deleted
     * @param totalDeleted matches already deleted by prior recursive calls in this chain
     * @param filter predicate selecting which fetched messages to delete
     * @param onComplete callback invoked with the final cumulative count for this channel
     */
    static void purgeChannelMessages(MessageChannel channel, String messageId, int remaining,
            int totalDeleted, Predicate<Message> filter, PurgeResult onComplete) {
        channel.getHistoryAfter(messageId, BATCH_SIZE).queue(history -> {
            List<Message> fetched = history.getRetrievedHistory();
            if (fetched.isEmpty()) {
                onComplete.run(totalDeleted);
                return;
            }

            List<Message> matches = fetched.stream().filter(filter).limit(remaining).toList();

            if (!matches.isEmpty()) {
                channel.purgeMessages(matches);
            }

            int newTotal = totalDeleted + matches.size();
            int newRemaining = remaining - matches.size();

            if (fetched.size() == BATCH_SIZE && newRemaining > 0) {
                purgeChannelMessages(channel, fetched.getFirst().getId(), newRemaining, newTotal,
                        filter, onComplete);
            } else {
                onComplete.run(newTotal);
            }
        }, failure -> {
            logger.warn("Failed to fetch history in channel {}: {}", channel.getName(),
                    failure.getMessage());
            onComplete.run(totalDeleted);
        });
    }
}
