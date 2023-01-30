package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

import java.util.regex.Pattern;

/**
 * Receives incoming Discord guild messages from channels matching a given pattern.
 * <p>
 * All message receivers have to implement this interface. For convenience, there is a
 * {@link MessageReceiverAdapter} available that implemented most methods already. A new receiver
 * can then be registered by adding it to {@link Features}.
 * <p>
 * <p>
 * After registration, the system will notify a receiver whenever a new message was sent or an
 * existing message was updated in any channel matching the {@link #getChannelNamePattern()} the bot
 * is added to.
 */
public interface MessageReceiver extends Feature {
    /**
     * Retrieves the pattern matching the names of channels of which this receiver is interested in
     * receiving sent messages from. Called by the core system once during the startup in order to
     * register the receiver accordingly.
     * <p>
     * Changes on the pattern returned by this method afterwards will not be picked up.
     *
     * @return the pattern matching the names of relevant channels
     */
    Pattern getChannelNamePattern();

    /**
     * Triggered by the core system whenever a new message was sent and received in a text channel
     * of a guild the bot has been added to.
     *
     * @param event the event that triggered this, containing information about the corresponding
     *        message that was sent and received
     */
    void onMessageReceived(MessageReceivedEvent event);

    /**
     * Triggered by the core system whenever an existing message was edited in a text channel of a
     * guild the bot has been added to.
     *
     * @param event the event that triggered this, containing information about the corresponding
     *        message that was edited
     */
    void onMessageUpdated(MessageUpdateEvent event);

    /**
     * Triggered by the core system whenever an existing message was deleted in a text channel of a
     * guild the bot has been added to.
     *
     * @param event the event that triggered this, containing information about the corresponding
     *        message that was deleted
     */
    void onMessageDeleted(MessageDeleteEvent event);
}
