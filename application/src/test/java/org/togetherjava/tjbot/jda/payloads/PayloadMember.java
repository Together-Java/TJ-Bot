package org.togetherjava.tjbot.jda.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("ClassWithTooManyFields")
public final class PayloadMember {
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
    private PayloadUser user;

    public PayloadMember(@Nullable String premiumSince, @Nullable String nick, String joinedAt,
            String permissions, List<String> roles, boolean pending, boolean deaf, boolean mute,
            @Nullable String avatar, boolean isPending, PayloadUser user) {
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

    public static PayloadMember of(Member member) {
        String permissions = Long
            .toString(Permission.getRaw(member.getPermissions().toArray(Permission[]::new)));
        List<String> roles = member.getRoles().stream().map(Role::getId).toList();
        PayloadUser user = PayloadUser.of(member.getUser());

        return new PayloadMember(null, member.getNickname(), member.getTimeJoined().toString(),
                permissions, roles, member.isPending(), false, false, member.getAvatarId(),
                member.isPending(), user);
    }

    public PayloadUser getUser() {
        return user;
    }

    public void setUser(PayloadUser user) {
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

    public String getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(String joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public List<String> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    public void setRoles(List<String> roles) {
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
