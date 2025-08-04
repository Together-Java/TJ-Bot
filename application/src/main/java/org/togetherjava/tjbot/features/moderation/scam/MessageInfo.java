package org.togetherjava.tjbot.features.moderation.scam;


import java.time.Instant;
import java.util.Objects;

/**
 * Information about a message, used to detect spam of the same message by the same user in
 * different channels.
 * 
 * @param userId the id of the user
 * @param channelId the channel where the message was posted
 * @param messageHash the hash of the message
 * @param timestamp when the message was posted
 */
public record MessageInfo(long userId, long channelId, String messageHash, Instant timestamp) {

    @Override
    public boolean equals(Object other) {
        return other instanceof MessageInfo message && this.userId == message.userId
                && this.channelId == message.channelId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, channelId);
    }
}
