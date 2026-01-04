package org.togetherjava.tjbot.features.voicechat;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.VoiceReceiverAdapter;

import java.util.List;
import java.util.regex.Pattern;

public class DynamicVoiceChat extends VoiceReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DynamicVoiceChat.class);
    private final List<Pattern> dynamicVoiceChannelPatterns;

    public DynamicVoiceChat(Config config) {
        this.dynamicVoiceChannelPatterns =
                config.getDynamicVoiceChannelPatterns().stream().map(Pattern::compile).toList();
    }

    @Override
    public void onVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        AudioChannelUnion channelJoined = event.getChannelJoined();
        AudioChannelUnion channelLeft = event.getChannelLeft();

        if (channelJoined != null && eventHappenOnDynamicRootChannel(channelJoined)) {
            logger.debug("Event happened on joined channel {}", channelJoined);
            createDynamicVoiceChannel(event, channelJoined.asVoiceChannel());
        }

        if (channelLeft != null && !eventHappenOnDynamicRootChannel(channelLeft)) {
            logger.debug("Event happened on left channel {}", channelLeft);
            deleteDynamicVoiceChannel(channelLeft);
        }
    }

    private boolean eventHappenOnDynamicRootChannel(AudioChannelUnion channel) {
        return dynamicVoiceChannelPatterns.stream()
            .anyMatch(pattern -> pattern.matcher(channel.getName()).matches());
    }

    private void createDynamicVoiceChannel(@NotNull GuildVoiceUpdateEvent event,
            VoiceChannel channel) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        String newChannelName = "%s's %s".formatted(member.getEffectiveName(), channel.getName());

        channel.createCopy()
            .setName(newChannelName)
            .setPosition(channel.getPositionRaw())
            .onSuccess(newChannel -> {
                moveMember(guild, member, newChannel);
                sendWarningEmbed(newChannel);
            })
            .queue(newChannel -> logger.trace("Successfully created {} voice channel.",
                    newChannel.getName()),
                    error -> logger.error("Failed to create dynamic voice channel", error));
    }

    private void moveMember(Guild guild, Member member, AudioChannel channel) {
        guild.moveVoiceMember(member, channel)
            .queue(_ -> logger.trace(
                    "Successfully moved {} to newly created dynamic voice channel {}",
                    member.getEffectiveName(), channel.getName()),
                    error -> logger.error(
                            "Failed to move user into dynamically created voice channel {}, {}",
                            member.getNickname(), channel.getName(), error));
    }

    private void deleteDynamicVoiceChannel(AudioChannelUnion channel) {
        int memberCount = channel.getMembers().size();

        if (memberCount > 0) {
            logger.debug("Voice channel {} not empty ({} members), so not removing.",
                    channel.getName(), memberCount);
            return;
        }

        channel.delete()
            .queue(_ -> logger.trace("Deleted dynamically created voice channel: {} ",
                    channel.getName()),
                    error -> logger.error("Failed to delete dynamically created voice channel: {} ",
                            channel.getName(), error));
    }

    private void sendWarningEmbed(VoiceChannel channel) {
        MessageEmbed messageEmbed = new EmbedBuilder()
            .addField("👋 Heads up!",
                    """
                            This is a **temporary** voice chat channel. Messages sent here will be *cleared* once \
                            the channel is deleted when everyone leaves. If you need to keep something important, \
                            make sure to save it elsewhere. 💬
                            """,
                    false)
            .build();

        channel.sendMessageEmbeds(messageEmbed).queue();
    }
}
