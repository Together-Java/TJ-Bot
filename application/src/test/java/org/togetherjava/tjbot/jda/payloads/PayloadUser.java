package org.togetherjava.tjbot.jda.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PayloadUser {
    private boolean bot;
    @JsonProperty("public_flags")
    private long publicFlags;
    private String id;
    private String avatar;
    private String username;
    private String discriminator;

    public PayloadUser(boolean bot, long publicFlags, String id, @Nullable String avatar,
            String username, String discriminator) {
        this.publicFlags = publicFlags;
        this.id = id;
        this.avatar = avatar;
        this.username = username;
        this.discriminator = discriminator;
    }

    @Nonnull
    public static PayloadUser of(User user) {
        return new PayloadUser(user.isBot(), user.getFlagsRaw(), user.getId(), user.getAvatarId(),
                user.getName(), user.getDiscriminator());
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

    @Nonnull
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

    @Nonnull
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Nonnull
    public String getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(String discriminator) {
        this.discriminator = discriminator;
    }
}
