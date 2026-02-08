package org.togetherjava.tjbot.features.voicechat;

import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.List;

/**
 * Voice chat cleanup strategy interface for handling voice chat archive removal.
 * <p>
 * See provided implementation {@link OldestVoiceChatCleanup} for a more concrete usage example.
 */
public interface VoiceChatCleanupStrategy {

    /**
     * Attempts to delete the {@link VoiceChannel} channels from the Discord guild found in the
     * inputted list.
     *
     * @param voiceChannels a list of voice channels to be considered for removal
     */
    void cleanup(List<VoiceChannel> voiceChannels);
}
