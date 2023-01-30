package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

import java.util.regex.Pattern;

/**
 * Adapter implementation of a {@link MessageReceiver}. A new receiver can then be registered by
 * adding it to {@link Features}.
 * <p>
 * {@link #onMessageReceived(MessageReceivedEvent)} and
 * {@link #onMessageUpdated(MessageUpdateEvent)} can be overridden if desired. The default
 * implementation is empty, the adapter will not react to such events.
 */
public abstract class MessageReceiverAdapter implements MessageReceiver {

    private final Pattern channelNamePattern;

    /**
     * Creates an instance of a message receiver, listening to messages of all channels.
     */
    protected MessageReceiverAdapter() {
        this(Pattern.compile(".*"));
    }

    /**
     * Creates an instance of a message receiver with the given pattern.
     *
     * @param channelNamePattern the pattern matching names of channels interested in, only messages
     *        from matching channels will be received
     */
    protected MessageReceiverAdapter(Pattern channelNamePattern) {
        this.channelNamePattern = channelNamePattern;
    }

    @Override
    public final Pattern getChannelNamePattern() {
        return channelNamePattern;
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onMessageDeleted(MessageDeleteEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }
}
