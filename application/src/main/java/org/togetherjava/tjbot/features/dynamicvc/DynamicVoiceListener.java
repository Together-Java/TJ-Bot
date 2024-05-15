package org.togetherjava.tjbot.features.dynamicvc;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.VoiceReceiverAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * {@link DynamicVoiceListener} is a feature that dynamically manages voice channels within a
 * Discord guild based on user activity.
 * <p>
 * It is designed to handle events related to voice channel updates (e.g. when users join or leave
 * voice channels). It dynamically creates or deletes voice channels to ensure there is always
 * <i>one</i> available empty channel for users to join, and removes duplicate empty channels to
 * avoid clutter.
 * <p>
 * This feature relies on configurations provided at initialization to determine the patterns for
 * channel names it should manage. The configuration is expected to provide a list of regular
 * expression patterns for these channel names.
 */
public class DynamicVoiceListener extends VoiceReceiverAdapter {

    private final Map<String, Predicate<String>> channelPredicates = new HashMap<>();
    private static final Pattern channelTopicPattern = Pattern.compile("(\\s+\\d+)$");

    /** Map of event queues for each channel topic. */
    private static final Map<String, Queue<GuildVoiceUpdateEvent>> eventQueues = new HashMap<>();

    /** Map to track if an event queue is currently being processed for each channel topic. */
    private static final Map<String, AtomicBoolean> activeQueuesMap = new HashMap<>();

    /**
     * Initializes a new {@link DynamicVoiceListener} with the specified configuration.
     *
     * @param config the configuration containing dynamic voice channel patterns
     */
    public DynamicVoiceListener(Config config) {
        config.getDynamicVoiceChannelPatterns().forEach(pattern -> {
            channelPredicates.put(pattern, Pattern.compile(pattern).asMatchPredicate());
            activeQueuesMap.put(pattern, new AtomicBoolean(false));
            eventQueues.put(pattern, new LinkedList<>());
        });
    }

    @Override
    public void onVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        AudioChannelUnion joinChannel = event.getChannelJoined();
        AudioChannelUnion leftChannel = event.getChannelLeft();

        if (joinChannel != null) {
            insertEventToQueue(event, getChannelTopic(joinChannel.getName()));
        }

        if (leftChannel != null) {
            insertEventToQueue(event, getChannelTopic(leftChannel.getName()));
        }
    }

    private void insertEventToQueue(GuildVoiceUpdateEvent event, String channelTopic) {
        var eventQueue = eventQueues.get(channelTopic);

        if (eventQueue == null) {
            return;
        }

        eventQueue.add(event);

        if (activeQueuesMap.get(channelTopic).get()) {
            return;
        }

        processEventFromQueue(channelTopic);
    }

    private void processEventFromQueue(String channelTopic) {
        AtomicBoolean activeQueueFlag = activeQueuesMap.get(channelTopic);
        GuildVoiceUpdateEvent event = eventQueues.get(channelTopic).poll();

        if (event == null) {
            activeQueueFlag.set(false);
            return;
        }

        activeQueueFlag.set(true);

        handleTopicUpdate(event, channelTopic);
    }

    private void handleTopicUpdate(GuildVoiceUpdateEvent event, String channelTopic) {
        AtomicBoolean activeQueueFlag = activeQueuesMap.get(channelTopic);
        Guild guild = event.getGuild();
        List<CompletableFuture<?>> restActionTasks = new ArrayList<>();

        if (channelPredicates.get(channelTopic) == null) {
            activeQueueFlag.set(false);
            return;
        }

        long emptyChannelsCount = getEmptyChannelsCountFromTopic(guild, channelTopic);

        if (emptyChannelsCount == 0) {
            long channelCount = getChannelCountFromTopic(guild, channelTopic);

            restActionTasks
                .add(makeCreateVoiceChannelFromTopicFuture(guild, channelTopic, channelCount));
        } else if (emptyChannelsCount != 1) {
            restActionTasks.addAll(makeRemoveDuplicateEmptyChannelsFutures(guild, channelTopic));
            restActionTasks.addAll(makeRenameTopicChannelsFutures(guild, channelTopic));
        }

        if (!restActionTasks.isEmpty()) {
            CompletableFuture.allOf(restActionTasks.toArray(CompletableFuture[]::new))
                .thenCompose(v -> {
                    List<CompletableFuture<?>> renameTasks =
                            makeRenameTopicChannelsFutures(guild, channelTopic);
                    return CompletableFuture.allOf(renameTasks.toArray(CompletableFuture[]::new));
                })
                .handle((result, exception) -> {
                    processEventFromQueue(channelTopic);
                    activeQueueFlag.set(false);
                    return null;
                });
            return;
        }

        processEventFromQueue(channelTopic);
        activeQueueFlag.set(false);
    }

    private static CompletableFuture<? extends StandardGuildChannel> makeCreateVoiceChannelFromTopicFuture(
            Guild guild, String channelTopic, long topicChannelsCount) {
        Optional<VoiceChannel> originalTopicChannelOptional =
                getOriginalTopicChannel(guild, channelTopic);

        if (originalTopicChannelOptional.isPresent()) {
            VoiceChannel originalTopicChannel = originalTopicChannelOptional.orElseThrow();

            return originalTopicChannel.createCopy()
                .setName(getNumberedChannelTopic(channelTopic, topicChannelsCount + 1))
                .setPosition(originalTopicChannel.getPositionRaw())
                .submit();
        }

        return CompletableFuture.completedFuture(null);
    }

    private static Optional<VoiceChannel> getOriginalTopicChannel(Guild guild,
            String channelTopic) {
        return guild.getVoiceChannels()
            .stream()
            .filter(channel -> channel.getName().equals(channelTopic))
            .findFirst();
    }

    private List<CompletableFuture<Void>> makeRemoveDuplicateEmptyChannelsFutures(Guild guild,
            String channelTopic) {
        List<VoiceChannel> channelsToRemove = getVoiceChannelsFromTopic(guild, channelTopic)
            .filter(channel -> channel.getMembers().isEmpty())
            .toList();
        final List<CompletableFuture<Void>> restActionTasks = new ArrayList<>();

        channelsToRemove.subList(1, channelsToRemove.size())
            .forEach(channel -> restActionTasks.add(channel.delete().submit()));

        return restActionTasks;
    }

    private List<CompletableFuture<?>> makeRenameTopicChannelsFutures(Guild guild,
            String channelTopic) {
        List<VoiceChannel> topicChannels = getVoiceChannelsFromTopic(guild, channelTopic).toList();
        List<CompletableFuture<?>> restActionTasks = new ArrayList<>();

        IntStream.range(0, topicChannels.size())
            .asLongStream()
            .mapToObj(channelId -> Pair.of(channelId + 1, topicChannels.get((int) channelId)))
            .filter(pair -> pair.getLeft() != 1)
            .forEach(pair -> {
                long channelId = pair.getLeft();
                VoiceChannel voiceChannel = pair.getRight();
                String voiceChannelNameTopic = getChannelTopic(voiceChannel.getName());

                restActionTasks.add(voiceChannel.getManager()
                    .setName(getNumberedChannelTopic(voiceChannelNameTopic, channelId))
                    .submit());
            });

        return restActionTasks;
    }

    private long getChannelCountFromTopic(Guild guild, String channelTopic) {
        return getVoiceChannelsFromTopic(guild, channelTopic).count();
    }

    private Stream<VoiceChannel> getVoiceChannelsFromTopic(Guild guild, String channelTopic) {
        return guild.getVoiceChannels()
            .stream()
            .filter(channel -> channelPredicates.get(channelTopic)
                .test(getChannelTopic(channel.getName())));
    }

    private long getEmptyChannelsCountFromTopic(Guild guild, String channelTopic) {
        return getVoiceChannelsFromTopic(guild, channelTopic)
            .map(channel -> channel.getMembers().size())
            .filter(number -> number == 0)
            .count();
    }

    private static String getChannelTopic(String channelName) {
        Matcher channelTopicPatternMatcher = channelTopicPattern.matcher(channelName);

        if (channelTopicPatternMatcher.find()) {
            return channelTopicPatternMatcher.replaceAll("");
        }

        return channelName;
    }

    private static String getNumberedChannelTopic(String channelTopic, long channelId) {
        return String.format("%s %d", channelTopic, channelId);
    }
}
