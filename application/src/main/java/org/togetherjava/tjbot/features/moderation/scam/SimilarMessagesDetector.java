package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.config.ScamBlockerConfig;
import org.togetherjava.tjbot.features.utils.Hashing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class which tries to detect scams by monitoring similar messages.
 */
public class SimilarMessagesDetector {
    private static final String HASH_METHOD = "SHA";

    private final ScamBlockerConfig scamBlockerConfig;
    private final Set<MessageInfo> messageCache;
    private final Set<Long> alreadyFlaggedUsers;

    /**
     * Creates an instance of this class by using the given config.
     * 
     * @param scamBlockerConfig the scam config
     */
    public SimilarMessagesDetector(ScamBlockerConfig scamBlockerConfig) {
        this.scamBlockerConfig = scamBlockerConfig;
        this.messageCache = new HashSet<>();
        this.alreadyFlaggedUsers = new HashSet<>();
    }

    private boolean shouldIgnore(Message message) {
        if (!message.getAttachments().isEmpty()) {
            return false;
        }
        if (message.getContentRaw().length() <= scamBlockerConfig.getSimilarMessageLengthIgnore()) {
            return true;
        }
        return scamBlockerConfig.getSimilarMessagesWhitelist()
            .contains(message.getContentRaw().toLowerCase());
    }

    private MessageInfo addToMessageCache(MessageReceivedEvent event) {
        long userId = event.getAuthor().getIdLong();
        long channelId = event.getChannel().getIdLong();
        String messageHash = getHash(event.getMessage());
        Instant timestamp = event.getMessage().getTimeCreated().toInstant();
        MessageInfo messageInfo = new MessageInfo(userId, channelId, messageHash, timestamp);
        messageCache.add(messageInfo);
        return messageInfo;
    }

    private String getHash(Message message) {
        String wholeText = message.getContentRaw() + message.getAttachments()
            .stream()
            .map(Message.Attachment::getFileName)
            .collect(Collectors.joining());
        return Hashing.bytesToHex(Hashing.hashUTF8(HASH_METHOD, wholeText));
    }

    private boolean hasPostedTooManySimilarMessages(long userId, String messageHash) {
        long similarMessageCount = messageCache.stream()
            .filter(m -> m.userId() == userId && m.messageHash().equals(messageHash)
                    && !isObsolete(m))
            .count();
        return similarMessageCount > scamBlockerConfig.getMaxAllowedSimilarMessages();
    }

    private boolean isObsolete(MessageInfo messageInfo) {
        return messageInfo.timestamp()
            .plus(scamBlockerConfig.getSimilarMessagesWindow(), ChronoUnit.MINUTES)
            .isBefore(Instant.now());
    }

    /**
     * Stores message data and if many messages of same author, different channel and same content
     * is posted several times, returns true.
     *
     * @param event the message event
     * @return true if the user spammed the message in several channels, false otherwise
     */
    public boolean doSimilarMessageCheck(MessageReceivedEvent event) {
        long userId = event.getAuthor().getIdLong();
        if (alreadyFlaggedUsers.contains(userId)) {
            return true;
        }
        if (shouldIgnore(event.getMessage())) {
            return false;
        }
        String hash = addToMessageCache(event).messageHash();
        if (hasPostedTooManySimilarMessages(userId, hash)) {
            alreadyFlaggedUsers.add(userId);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Has to be called often to clear the cache.
     */
    public void runRoutine() {
        messageCache.removeIf(this::isObsolete);
    }
}
