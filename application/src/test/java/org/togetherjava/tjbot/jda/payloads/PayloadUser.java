package org.togetherjava.tjbot.jda.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PayloadUser {
    private boolean bot;
    @JsonProperty("public_flags")
    private long publicFlags;
    private String id;
    private String avatar;
    private String username;
    private String discriminator;

    public PayloadUser(boolean bot, long publicFlags, @NotNull String id, @Nullable String avatar,
            @NotNull String username, @NotNull String discriminator) {
        this.publicFlags = publicFlags;
        this.id = id;
        this.avatar = avatar;
        this.username = username;
        this.discriminator = discriminator;
    }

    public static @NotNull PayloadUser of(@NotNull User user) {
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

    @NotNull
    public String getId() {
        return id;
    }

    public void setId(@NotNull String id) {
        this.id = id;
    }

    @Nullable
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(@Nullable String avatar) {
        this.avatar = avatar;
    }

    @NotNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NotNull String username) {
        this.username = username;
    }

    @NotNull
    public String getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(@NotNull String discriminator) {
        this.discriminator = discriminator;
    }
}
