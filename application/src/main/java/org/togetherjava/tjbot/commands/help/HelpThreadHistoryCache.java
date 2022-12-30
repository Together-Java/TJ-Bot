package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * The HelpThreadHistoryCache class is a singleton that stores a mapping of message channels to a
 * linked list of message IDs. The cache allows for the retrieval of the most recent message ID for
 * a given message channel.
 * 
 * @author mDyingStar
 */
public class HelpThreadHistoryCache {

    private static HelpThreadHistoryCache instance = null;
    private final Map<MessageChannel, LinkedList<Integer>> messageChannelMessageIdMap;

    /**
     * Private constructor to prevent external instantiation of the cache.
     */
    private HelpThreadHistoryCache() {
        messageChannelMessageIdMap = new HashMap<>();
    }

    /**
     * Returns the singleton instance of the cache.
     * 
     * @return the instance of the cache.
     */
    public static HelpThreadHistoryCache getInstance() {
        if (instance == null) {
            instance = new HelpThreadHistoryCache();
        }
        return instance;
    }

    /**
     * Adds a message ID to the linked list of message IDs for the given message channel.
     * 
     * @param channel the message channel
     * @param messageId the message ID to add
     */
    public void add(MessageChannel channel, Integer messageId) {
        messageChannelMessageIdMap.computeIfAbsent(channel, ids -> new LinkedList<>());
        LinkedList<Integer> messageIds = messageChannelMessageIdMap.get(channel);
        messageIds.add(messageId);
    }

    /**
     * Returns the most recent message ID for the given message channel.
     * 
     * @param channel the message channel
     * @return the most recent message ID
     */
    public Integer getMostRecentMessageId(MessageChannel channel) {
        LinkedList<Integer> messageIds = messageChannelMessageIdMap.get(channel);
        return messageIds.get(messageIds.size() - 1);
    }
}
