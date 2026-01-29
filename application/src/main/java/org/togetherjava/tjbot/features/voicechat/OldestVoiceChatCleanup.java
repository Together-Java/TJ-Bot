package org.togetherjava.tjbot.features.voicechat;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.Comparator;
import java.util.List;

/**
 * Cleans up voice chats from an archive prioritizing the oldest {@link VoiceChannel}.
 * <p>
 * Considering a list of voice channels is provided with all of them obviously having a different
 * addition time, the first few {@link VoiceChannel} elements provided, amounting to the value of
 * <code>cleanChannelsAmount</code> will be removed from the guild.
 * <p>
 * The cleanup strategy will <i>not</i> be executed if the amount of voice channels does not exceed
 * the value of <code>minimumChannelsAmountToTrigger</code>.
 */
final class OldestVoiceChatCleanup implements VoiceChatCleanupStrategy {

    private final int cleanChannelsAmount;
    private final int minimumChannelsAmountToTrigger;

    OldestVoiceChatCleanup(int cleanChannelsAmount, int minimumChannelsAmountToTrigger) {
        this.cleanChannelsAmount = cleanChannelsAmount;
        this.minimumChannelsAmountToTrigger = minimumChannelsAmountToTrigger;
    }

    @Override
    public void cleanup(List<VoiceChannel> voiceChannels) {
        if (voiceChannels.size() < minimumChannelsAmountToTrigger) {
            return;
        }

        voiceChannels.stream()
            .sorted(Comparator.comparing(ISnowflake::getTimeCreated))
            .limit(cleanChannelsAmount)
            .forEach(voiceChannel -> voiceChannel.delete().queue());
    }
}
