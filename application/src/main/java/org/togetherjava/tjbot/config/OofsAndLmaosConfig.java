package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("oofsAndLmaos")
public final class OofsAndLmaosConfig {
    private final String oofEmojiName;
    private final String lmaoEmojiName;
    private final long starboardChannelId;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public OofsAndLmaosConfig(String oofEmojiName, String lmaoEmojiName, long starboardChannelId) {
        this.oofEmojiName = oofEmojiName;
        this.lmaoEmojiName = lmaoEmojiName;
        this.starboardChannelId = starboardChannelId;
    }

    public String getOofEmojiName() {
        return oofEmojiName;
    }

    public String getLmaoEmojiName() {
        return lmaoEmojiName;
    }

    public long getStarboardChannelId() {
        return starboardChannelId;
    }
}
