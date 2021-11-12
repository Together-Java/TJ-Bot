package org.togetherjava.tjbot.jda;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

final class PayloadSlashCommandUser {
    @JsonProperty("public_flags")
    private int publicFlags;
    private String id;
    private String avatar;
    private String username;
    private String discriminator;

    PayloadSlashCommandUser(int publicFlags, @NotNull String id, @NotNull String avatar,
            @NotNull String username, @NotNull String discriminator) {
        this.publicFlags = publicFlags;
        this.id = id;
        this.avatar = avatar;
        this.username = username;
        this.discriminator = discriminator;
    }

    public int getPublicFlags() {
        return publicFlags;
    }

    public void setPublicFlags(int publicFlags) {
        this.publicFlags = publicFlags;
    }

    @NotNull
    public String getId() {
        return id;
    }

    public void setId(@NotNull String id) {
        this.id = id;
    }

    @NotNull
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(@NotNull String avatar) {
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
