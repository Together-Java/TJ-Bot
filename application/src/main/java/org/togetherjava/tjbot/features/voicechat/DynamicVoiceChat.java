package org.togetherjava.tjbot.features.voicechat;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.managers.channel.middleman.AudioChannelManager;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.DynamicVoiceChatConfig;
import org.togetherjava.tjbot.features.VoiceReceiverAdapter;

import java.util.Optional;

/**
 * Handles dynamic voice channel creation and deletion based on user activity.
 * <p>
 * When a member joins a configured root channel, a temporary copy is created and the member is
 * moved into it. Once the channel becomes empty, it is archived and further deleted using a
 * {@link VoiceChatCleanupStrategy}.
 */
public final class DynamicVoiceChat extends VoiceReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DynamicVoiceChat.class);

    private final VoiceChatCleanupStrategy voiceChatCleanupStrategy;
    private final DynamicVoiceChatConfig dynamicVoiceChannelConfig;

    public DynamicVoiceChat(Config config) {
        this.dynamicVoiceChannelConfig = config.getDynamicVoiceChatConfig();

        this.voiceChatCleanupStrategy =
                new OldestVoiceChatCleanup(dynamicVoiceChannelConfig.cleanChannelsAmount(),
                        dynamicVoiceChannelConfig.minimumChannelsAmount());
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

            MessageHistory messageHistory = channelLeft.asVoiceChannel().getHistory();
            messageHistory.retrievePast(2).queue(messages -> {
                // Don't forget that there is always one
                // embed message sent by the bot every time.
                if (messages.size() > 1) {
                    archiveDynamicVoiceChannel(channelLeft);
                } else {
                    channelLeft.delete().queue();
                }
            });
        }
    }

    private boolean eventHappenOnDynamicRootChannel(AudioChannelUnion channel) {
        return dynamicVoiceChannelConfig.dynamicChannelPatterns()
            .stream()
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

    private void archiveDynamicVoiceChannel(AudioChannelUnion channel) {
        int memberCount = channel.getMembers().size();
        String channelName = channel.getName();

        if (memberCount > 0) {
            logger.debug("Voice channel {} not empty ({} members), so not removing.", channelName,
                    memberCount);
            return;
        }

        Optional<Category> archiveCategoryOptional = channel.getGuild()
            .getCategoryCache()
            .stream()
            .filter(c -> c.getName()
                .equalsIgnoreCase(dynamicVoiceChannelConfig.archiveCategoryPattern()))
            .findFirst();

        AudioChannelManager<?, ?> channelManager = channel.getManager();
        RestAction<Void> restActionChain =
                channelManager.setName(String.format("%s (Archived)", channelName))
                    .and(channel.getPermissionContainer().getManager().clearOverridesAdded());

        if (archiveCategoryOptional.isEmpty()) {
            logger.warn("Could not find archive category. Attempting to create one...");
            channel.getGuild()
                .createCategory(dynamicVoiceChannelConfig.archiveCategoryPattern())
                .queue(newCategory -> restActionChain.and(channelManager.setParent(newCategory))
                    .queue());
            return;
        }

        archiveCategoryOptional.ifPresent(archiveCategory -> restActionChain
            .and(channelManager.setParent(archiveCategory))
            .queue(_ -> voiceChatCleanupStrategy.cleanup(archiveCategory.getVoiceChannels()),
                    err -> logger.error("Could not archive dynamic voice chat", err)));
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
