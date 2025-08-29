package org.togetherjava.tjbot.features.utils;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ForumPostAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.*;

public final class ForumPoster {
    private final JDA jda;

    private ForumPoster(JDA jda) {
        this.jda = jda;
    }

    public static ForumPoster using(JDA jda) {
        return new ForumPoster(jda);
    }

    private ForumChannel findForumChannel(String channelId) {
        return Optional.ofNullable(jda.getForumChannelById(channelId))
            .orElseThrow(() -> new IllegalArgumentException(
                    "Did not find a forum channel with ID %s while trying to create a forum post. Make sure the config is setup properly."));
    }

    private ThreadChannel findForumPost(String postId) {
        return Optional.ofNullable(jda.getThreadChannelById(postId))
            .orElseThrow(() -> new IllegalArgumentException(
                    "Did not find the forum post with ID %s while trying to reply to a post."));

    }

    public MessageCreateAction sendPost(String postId, MessageCreateData messageData) {
        return sendPost(findForumPost(postId), messageData);
    }

    public MessageCreateAction sendPost(ThreadChannel post, MessageCreateData messageData) {
        return post.sendMessage(messageData);
    }

    public ForumPostAction createPost(String channelId, String title, MessageCreateData message) {
        return createPost(findForumChannel(channelId), title, message);
    }

    public ForumPostAction createPost(ForumChannel channel, String title,
            MessageCreateData message) {
        return channel.createForumPost(title, message);
    }
}
