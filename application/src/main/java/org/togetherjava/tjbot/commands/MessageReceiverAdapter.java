package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter implementation of a {@link MessageReceiver}. A new receiver can then be registered by
 * adding it to {@link Features}.
 * <p>
 * {@link #onMessageReceived(GuildMessageReceivedEvent)} and
 * {@link #onMessageUpdated(GuildMessageUpdateEvent)} can be overridden if desired. The default
 * implementation is empty, the adapter will not react to such events.
 */
public abstract class MessageReceiverAdapter implements MessageReceiver {

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onMessageUpdated(@NotNull GuildMessageUpdateEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }
}
