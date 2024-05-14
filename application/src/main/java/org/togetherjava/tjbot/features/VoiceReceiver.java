package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceStreamEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceVideoEvent;

import java.util.regex.Pattern;

/**
 * Receives incoming Discord guild events from voice channels matching a given pattern.
 * <p>
 * All voice receivers have to implement this interface. For convenience, there is a
 * {@link VoiceReceiverAdapter} available that implemented most methods already. A new receiver can
 * then be registered by adding it to {@link Features}.
 * <p>
 * <p>
 * After registration, the system will notify a receiver whenever a new event was sent or an
 * existing event was updated in any channel matching the {@link #getChannelNamePattern()} the bot
 * is added to.
 */
public interface VoiceReceiver extends Feature {
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
     * Triggered by the core system whenever a member joined, left or moved voice channels.
     *
     * @param event the event that triggered this
     */
    void onVoiceUpdate(GuildVoiceUpdateEvent event);

    /**
     * Triggered by the core system whenever a member toggled their camera in a voice channel.
     *
     * @param event the event that triggered this
     */
    void onVideoToggle(GuildVoiceVideoEvent event);

    /**
     * Triggered by the core system whenever a member started or stopped a stream.
     *
     * @param event the event that triggered this
     */
    void onStreamToggle(GuildVoiceStreamEvent event);

    /**
     * Triggered by the core system whenever a member toggled their mute status.
     *
     * @param event the event that triggered this
     */
    void onMuteToggle(GuildVoiceMuteEvent event);

    /**
     * Triggered by the core system whenever a member toggled their deafened status.
     *
     * @param event the event that triggered this
     */
    void onDeafenToggle(GuildVoiceDeafenEvent event);
}
