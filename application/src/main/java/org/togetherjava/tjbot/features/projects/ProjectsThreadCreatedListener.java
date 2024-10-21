package org.togetherjava.tjbot.features.projects;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.EventReceiver;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Listens for new threads being created in the "projects" forum and pins the first message. *
 * {@link Config#getProjectsChannelPattern()}.
 */
public final class ProjectsThreadCreatedListener extends ListenerAdapter implements EventReceiver {
    private final String configProjectsChannelPattern;
    private final Cache<Long, Instant> threadIdToCreatedAtCache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(2, TimeUnit.of(ChronoUnit.MINUTES))
        .build();

    public ProjectsThreadCreatedListener(Config config) {
        configProjectsChannelPattern = config.getProjectsChannelPattern();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromThread()) {
            ThreadChannel threadChannel = event.getChannel().asThreadChannel();
            Channel parentChannel = threadChannel.getParentChannel();
            boolean isPost = isPostMessage(threadChannel);

            if (parentChannel.getName().equals(configProjectsChannelPattern) && isPost) {
                pinParentMessage(event);
            }
        }
    }

    private boolean wasThreadAlreadyHandled(long threadChannelId) {
        Instant now = Instant.now();
        Instant createdAt = threadIdToCreatedAtCache.get(threadChannelId, any -> now);
        return createdAt != now;
    }

    private boolean isPostMessage(ThreadChannel threadChannel) {
        int messageCount = threadChannel.getMessageCount();
        if (messageCount <= 1 && !wasThreadAlreadyHandled(threadChannel.getIdLong())) {
            return threadChannel.retrieveMessageById(threadChannel.getIdLong())
                .map(message -> message.getIdLong() == threadChannel.getIdLong())
                .complete();
        }
        return false;
    }

    private void pinParentMessage(MessageReceivedEvent event) {
        event.getMessage().pin().queue();
    }
}
