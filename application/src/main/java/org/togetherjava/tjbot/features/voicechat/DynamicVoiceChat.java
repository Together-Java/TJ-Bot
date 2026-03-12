package org.togetherjava.tjbot.features.voicechat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.managers.channel.middleman.AudioChannelManager;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.DynamicVoiceChatConfig;
import org.togetherjava.tjbot.features.VoiceReceiverAdapter;
import org.togetherjava.tjbot.features.analytics.Metrics;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private final Metrics metrics;

    private final Cache<Long, Boolean> deletedChannels =
            Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

    /**
     * Creates a new instance of {@code DynamicVoiceChat}
     *
     * @param config the configurations needed for this feature. See:
     *        {@link org.togetherjava.tjbot.config.DynamicVoiceChatConfig}
     * @param metrics to track events
     */
    public DynamicVoiceChat(Config config, Metrics metrics) {
        this.dynamicVoiceChannelConfig = config.getDynamicVoiceChatConfig();
        this.metrics = metrics;

        this.voiceChatCleanupStrategy =
                new OldestVoiceChatCleanup(dynamicVoiceChannelConfig.cleanChannelsAmount(),
                        dynamicVoiceChannelConfig.minimumChannelsAmount());
    }

    @Override
    public void onVoiceUpdate(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();
        User user = member.getUser();

        AudioChannelUnion channelJoined = event.getChannelJoined();
        AudioChannelUnion channelLeft = event.getChannelLeft();

        if (channelJoined != null && isVoiceChannel(channelJoined) && !user.isBot()) {
            handleVoiceChannelJoin(event, channelJoined);
        }

        if (channelLeft != null && isVoiceChannel(channelLeft)) {
            handleVoiceChannelLeave(channelLeft);
        }
    }

    private void handleVoiceChannelJoin(GuildVoiceUpdateEvent event,
            AudioChannelUnion channelJoined) {
        if (eventHappenOnDynamicRootChannel(channelJoined)) {
            logger.debug("Event happened on joined channel {}", channelJoined);
            createDynamicVoiceChannel(event, channelJoined.asVoiceChannel());
        }
    }

    private synchronized void handleVoiceChannelLeave(AudioChannelUnion channelLeft) {
        long channelId = channelLeft.getIdLong();

        if (Boolean.TRUE.equals(deletedChannels.getIfPresent(channelId))) {
            return;
        }

        if (!eventHappenOnDynamicRootChannel(channelLeft)) {
            logger.debug("Event happened on left channel {}", channelLeft);

            if (hasMembers(channelLeft)) {
                logger.debug("Voice channel {} not empty, so not doing anything.",
                        channelLeft.getName());
                return;
            }

            channelLeft.asVoiceChannel().getHistory().retrievePast(2).queue(messages -> {
                if (messages.size() > 1) {
                    archiveDynamicVoiceChannel(channelLeft);
                } else {
                    deletedChannels.put(channelId, true);
                    try {
                        channelLeft.delete().queue();
                    } catch (Exception _) {
                        // Ignore
                    }
                }
            });
        }
    }

    private boolean eventHappenOnDynamicRootChannel(AudioChannelUnion channel) {
        return dynamicVoiceChannelConfig.dynamicChannelPatterns()
            .stream()
            .anyMatch(pattern -> pattern.matcher(channel.getName()).matches());
    }

    private void createDynamicVoiceChannel(GuildVoiceUpdateEvent event, VoiceChannel channel) {
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
            .queue(newChannel -> {
                logger.trace("Successfully created {} voice channel.", newChannel.getName());
                metrics.count("dynamic_voice_channel-created");
            }, error -> logger.error("Failed to create dynamic voice channel", error));
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

    private boolean hasMembers(AudioChannelUnion channel) {
        return !channel.getMembers().isEmpty();
    }

    private void archiveDynamicVoiceChannel(AudioChannelUnion channel) {
        String channelName = channel.getName();

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
            logger.error("Could not find category matching {}",
                    dynamicVoiceChannelConfig.archiveCategoryPattern());
            return;
        }

        archiveCategoryOptional.ifPresent(archiveCategory -> restActionChain
            .and(channelManager.setParent(archiveCategory))
            .and(channelManager.sync(archiveCategory))
            .queue(_ -> voiceChatCleanupStrategy.cleanup(archiveCategory.getVoiceChannels()),
                    err -> logger.error("Could not archive dynamic voice chat", err)));
    }

    private static void sendWarningEmbed(VoiceChannel channel) {
        channel
            .sendMessageEmbeds(
                    new EmbedBuilder()
                        .addField("👋 Heads up!",
                                """
                                        This is a **temporary** voice chat channel. Messages sent here will be *cleared* once \
                                        the channel is deleted when everyone leaves. If you need to keep something important, \
                                        make sure to save it elsewhere. 💬
                                        """,
                                false)
                        .build())
            .queue();
    }

    private static boolean isVoiceChannel(AudioChannelUnion channel) {
        return channel.getType() == ChannelType.VOICE;
    }
}
