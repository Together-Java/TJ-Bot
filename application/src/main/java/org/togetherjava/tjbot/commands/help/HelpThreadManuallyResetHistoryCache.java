package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.*;

/**
 * stores a map of message channels to a list of message ids.
 *
 * @author mDyingStar
 */
public final class HelpThreadManuallyResetHistoryCache {

    private static HelpThreadManuallyResetHistoryCache instance = null;
    private final Map<MessageChannel, List<String>> messageChannelMessageIdMap;

    /**
     * Private constructor to prevent external instantiation of the cache.
     */
    private HelpThreadManuallyResetHistoryCache() {
        messageChannelMessageIdMap = new HashMap<>();
    }

    /**
     * Returns the singleton instance of the cache.
     * 
     * @return the instance of the cache.
     */
    public static HelpThreadManuallyResetHistoryCache getInstance() {
        if (instance == null) {
            instance = new HelpThreadManuallyResetHistoryCache();
        }
        return instance;
    }

    /**
     * Adds a message id to the list for a given channel in the cache.
     *
     * @param channel the message channel to add the message id to
     * @param messageId the message id to add to the list for the channel
     */
    public void add(MessageChannel channel, String messageId) {
        messageChannelMessageIdMap.computeIfAbsent(channel, ids -> new LinkedList<>());
        List<String> messageIds = messageChannelMessageIdMap.get(channel);
        messageIds.add(messageId);
    }

    /**
     * Gets the most recent message id from the list for a given channel in the cache.
     *
     * @param channel the message channel to get the most recent message id from
     * @return the most recent message id from the list for the channel, or an empty string if the
     *         list is empty
     */
    public String getMostRecentMessageId(MessageChannel channel) {
        messageChannelMessageIdMap.computeIfAbsent(channel, messageIds -> new ArrayList<>());
        List<String> messageIds = messageChannelMessageIdMap.get(channel);
        return messageIds.isEmpty() ? "" : messageIds.get(messageIds.size() - 1);
    }
}
