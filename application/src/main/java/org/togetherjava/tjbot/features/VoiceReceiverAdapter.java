package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceStreamEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceVideoEvent;

import java.util.regex.Pattern;

/**
 * Adapter implementation of a {@link VoiceReceiver}. A new receiver can then be registered by
 * adding it to {@link Features}.
 * <p>
 * {@link #onVoiceUpdate(GuildVoiceUpdateEvent)} like the other provided methods can be overridden
 * if desired. The default implementation is empty, the adapter will not react to such events.
 */
public class VoiceReceiverAdapter implements VoiceReceiver {

    private final Pattern channelNamePattern;

    protected VoiceReceiverAdapter() {
        this(Pattern.compile(".*"));
    }

    protected VoiceReceiverAdapter(Pattern channelNamePattern) {
        this.channelNamePattern = channelNamePattern;
    }

    @Override
    public Pattern getChannelNamePattern() {
        return channelNamePattern;
    }

    @Override
    public void onVoiceUpdate(GuildVoiceUpdateEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @Override
    public void onVideoToggle(GuildVoiceVideoEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @Override
    public void onStreamToggle(GuildVoiceStreamEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @Override
    public void onMuteToggle(GuildVoiceMuteEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @Override
    public void onDeafenToggle(GuildVoiceDeafenEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }
}
