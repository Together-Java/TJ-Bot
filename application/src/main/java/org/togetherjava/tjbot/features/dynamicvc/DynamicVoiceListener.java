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

public class DynamicVoiceListener extends VoiceReceiverAdapter {

    private final Map<String, Predicate<String>> patterns = new HashMap<>();
    private static final Pattern channelTopicPattern = Pattern.compile("(\\s+\\d+)$");
    private static final Map<String, Queue<GuildVoiceUpdateEvent>> eventQueues = new HashMap<>();
    private static final Map<String, AtomicBoolean> isEventProcessing = new HashMap<>();

    public DynamicVoiceListener(Config config) {
        config.getDynamicVoiceChannelPatterns().forEach(pattern -> {
            patterns.put(pattern, Pattern.compile(pattern).asMatchPredicate());
            isEventProcessing.put(pattern, new AtomicBoolean(false));
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

        if (isEventProcessing.get(channelTopic).get()) {
            return;
        }

        processEventFromQueue(channelTopic);
    }

    private void processEventFromQueue(String channelTopic) {
        AtomicBoolean processing = isEventProcessing.get(channelTopic);
        GuildVoiceUpdateEvent event = eventQueues.get(channelTopic).poll();

        if (event == null) {
            processing.set(false);
            return;
        }

        processing.set(true);

        handleTopicUpdate(event, channelTopic);
    }

    private void handleTopicUpdate(GuildVoiceUpdateEvent event, String channelTopic) {
        AtomicBoolean processing = isEventProcessing.get(channelTopic);
        Guild guild = event.getGuild();
        List<CompletableFuture<?>> tasks = new ArrayList<>();

        if (patterns.get(channelTopic) == null) {
            processing.set(false);
            return;
        }

        long emptyChannelsCount = getEmptyChannelsCountFromTopic(guild, channelTopic);

        if (emptyChannelsCount == 0) {
            long channelCount = getChannelCountFromTopic(guild, channelTopic);

            tasks.add(createVoiceChannelFromTopic(guild, channelTopic, channelCount));
        } else if (emptyChannelsCount != 1) {
            tasks.addAll(removeDuplicateEmptyChannels(guild, channelTopic));
            tasks.addAll(renameTopicChannels(guild, channelTopic));
        }

        if (!tasks.isEmpty()) {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).thenCompose(v -> {
                List<CompletableFuture<?>> renameTasks = renameTopicChannels(guild, channelTopic);
                return CompletableFuture.allOf(renameTasks.toArray(CompletableFuture[]::new));
            }).handle((result, exception) -> {
                processEventFromQueue(channelTopic);
                processing.set(false);
                return null;
            });
            return;
        }

        processEventFromQueue(channelTopic);
        processing.set(false);
    }

    private static CompletableFuture<? extends StandardGuildChannel> createVoiceChannelFromTopic(
            Guild guild, String channelTopic, long topicChannelsCount) {
        Optional<VoiceChannel> voiceChannelOptional = getOriginalTopicChannel(guild, channelTopic);

        if (voiceChannelOptional.isPresent()) {
            VoiceChannel originalChannel = voiceChannelOptional.orElseThrow();

            return originalChannel.createCopy()
                .setName(getNumberedChannelTopic(channelTopic, topicChannelsCount + 1))
                .setPosition(originalChannel.getPositionRaw())
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

    private List<CompletableFuture<Void>> removeDuplicateEmptyChannels(Guild guild,
            String channelTopic) {
        List<VoiceChannel> channelsToRemove = getVoiceChannelsFromTopic(guild, channelTopic)
            .filter(channel -> channel.getMembers().isEmpty())
            .toList();
        final List<CompletableFuture<Void>> tasks = new ArrayList<>();

        channelsToRemove.subList(1, channelsToRemove.size())
            .forEach(channel -> tasks.add(channel.delete().submit()));

        return tasks;
    }

    private List<CompletableFuture<?>> renameTopicChannels(Guild guild, String channelTopic) {
        List<VoiceChannel> channels = getVoiceChannelsFromTopic(guild, channelTopic).toList();
        List<CompletableFuture<?>> tasks = new ArrayList<>();

        IntStream.range(0, channels.size())
            .asLongStream()
            .mapToObj(number -> Pair.of(number + 1, channels.get((int) number)))
            .filter(pair -> pair.getLeft() != 1)
            .forEach(pair -> {
                long number = pair.getLeft();
                VoiceChannel voiceChannel = pair.getRight();
                String voiceChannelNameTopic = getChannelTopic(voiceChannel.getName());

                tasks.add(voiceChannel.getManager()
                    .setName(getNumberedChannelTopic(voiceChannelNameTopic, number))
                    .submit());
            });

        return tasks;
    }

    private long getChannelCountFromTopic(Guild guild, String channelTopic) {
        return getVoiceChannelsFromTopic(guild, channelTopic).count();
    }

    private Stream<VoiceChannel> getVoiceChannelsFromTopic(Guild guild, String channelTopic) {
        return guild.getVoiceChannels()
            .stream()
            .filter(channel -> patterns.get(channelTopic).test(getChannelTopic(channel.getName())));
    }

    private long getEmptyChannelsCountFromTopic(Guild guild, String channelTopic) {
        return getVoiceChannelsFromTopic(guild, channelTopic)
            .map(channel -> channel.getMembers().size())
            .filter(number -> number == 0)
            .count();
    }

    private static String getChannelTopic(String channelName) {
        Matcher matcher = channelTopicPattern.matcher(channelName);

        if (matcher.find()) {
            return matcher.replaceAll("");
        }

        return channelName;
    }

    private static String getNumberedChannelTopic(String channelTopic, long id) {
        return String.format("%s %d", channelTopic, id);
    }
}
