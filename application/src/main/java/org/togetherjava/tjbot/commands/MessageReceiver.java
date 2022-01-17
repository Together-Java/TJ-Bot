package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Receives incoming Discord guild messages.
 * <p>
 * All message receivers have to implement this interface. For convenience, there is a
 * {@link MessageReceiverAdapter} available that implemented most methods already. A new receiver
 * can then be registered by adding it to {@link Features}.
 * <p>
 * <p>
 * After registration, the system will notify a receiver whenever a new message was sent or an
 * existing message was updated in any of the guilds the bot is added to.
 */
public interface MessageReceiver extends Feature {
    /**
     * Triggered by the core system whenever a new message was sent in a text channel of a guild the
     * bot has been added to.
     *
     * @param event the event that triggered this, containing information about the corresponding
     *        message that was sent
     */
    void onMessageSent(@NotNull GuildMessageReceivedEvent event);

    /**
     * Triggered by the core system whenever an existing message was edited in a text channel of a
     * guild the bot has been added to.
     *
     * @param event the event that triggered this, containing information about the corresponding
     *        message that was edited
     */
    void onMessageUpdated(@NotNull GuildMessageUpdateEvent event);
}
