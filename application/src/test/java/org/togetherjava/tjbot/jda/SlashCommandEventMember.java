package org.togetherjava.tjbot.jda;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("ClassWithTooManyFields")
final class SlashCommandEventMember {
    @JsonProperty("premium_since")
    private String premiumSince;
    private String nick;
    @JsonProperty("joined_at")
    private String joinedAt;
    private String permissions;
    private List<String> roles;
    private boolean pending;
    private boolean deaf;
    private boolean mute;
    private String avatar;
    @JsonProperty("is_pending")
    private boolean isPending;
    private SlashCommandEventUser user;

    @SuppressWarnings("ConstructorWithTooManyParameters")
    SlashCommandEventMember(@Nullable String premiumSince, @Nullable String nick,
            @NotNull String joinedAt, @NotNull String permissions, @NotNull List<String> roles,
            boolean pending, boolean deaf, boolean mute, @Nullable String avatar, boolean isPending,
            SlashCommandEventUser user) {
        this.premiumSince = premiumSince;
        this.nick = nick;
        this.joinedAt = joinedAt;
        this.permissions = permissions;
        this.roles = new ArrayList<>(roles);
        this.pending = pending;
        this.deaf = deaf;
        this.mute = mute;
        this.avatar = avatar;
        this.isPending = isPending;
        this.user = user;
    }

    public @NotNull SlashCommandEventUser getUser() {
        return user;
    }

    public void setUser(@NotNull SlashCommandEventUser user) {
        this.user = user;
    }

    @Nullable
    public String getPremiumSince() {
        return premiumSince;
    }

    public void setPremiumSince(@Nullable String premiumSince) {
        this.premiumSince = premiumSince;
    }

    @Nullable
    public String getNick() {
        return nick;
    }

    public void setNick(@Nullable String nick) {
        this.nick = nick;
    }

    @NotNull
    public String getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(@NotNull String joinedAt) {
        this.joinedAt = joinedAt;
    }

    @NotNull
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(@NotNull String permissions) {
        this.permissions = permissions;
    }

    @NotNull
    public List<String> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    public void setRoles(@NotNull List<String> roles) {
        this.roles = new ArrayList<>(roles);
    }

    // NOTE This is a problem in Discord, which has "pending" and "isPending" fields both at the
    // same time.
    @SuppressWarnings("SuspiciousGetterSetter")
    public boolean isPending() {
        return isPending;
    }

    public void setIsPending(boolean isPending) {
        this.isPending = isPending;
    }

    // NOTE This is a problem in Discord, which has "pending" and "isPending" fields both at the
    // same time.
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    public boolean getPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public boolean isDeaf() {
        return deaf;
    }

    public void setDeaf(boolean deaf) {
        this.deaf = deaf;
    }

    public boolean isMute() {
        return mute;
    }

    public void setMute(boolean mute) {
        this.mute = mute;
    }

    @Nullable
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(@Nullable String avatar) {
        this.avatar = avatar;
    }
}
