package org.togetherjava.tjbot.jda.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;

public final class PayloadUser {
    private boolean bot;
    @JsonProperty("public_flags")
    private long publicFlags;
    private String id;
    private String avatar;
    private String username;

    public PayloadUser(boolean bot, long publicFlags, String id, @Nullable String avatar,
            String username) {
        this.bot = bot;
        this.publicFlags = publicFlags;
        this.id = id;
        this.avatar = avatar;
        this.username = username;
    }

    public static PayloadUser of(User user) {
        return new PayloadUser(user.isBot(), user.getFlagsRaw(), user.getId(), user.getAvatarId(),
                user.getName());
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }

    public long getPublicFlags() {
        return publicFlags;
    }

    public void setPublicFlags(long publicFlags) {
        this.publicFlags = publicFlags;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Nullable
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(@Nullable String avatar) {
        this.avatar = avatar;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
