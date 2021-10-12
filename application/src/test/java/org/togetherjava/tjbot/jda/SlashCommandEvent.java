package org.togetherjava.tjbot.jda;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

final class SlashCommandEvent {
    @JsonProperty("guild_id")
    private String guildId;
    private String id;
    private int type;
    private int version;
    @JsonProperty("channel_id")
    private String channelId;
    @JsonProperty("application_id")
    private String applicationId;
    private String token;
    private SlashCommandEventMember member;
    private SlashCommandEventData data;

    SlashCommandEvent(@NotNull String guildId, @NotNull String id, int type, int version,
            @NotNull String channelId, @NotNull String applicationId, @NotNull String token,
            @NotNull SlashCommandEventMember member, @NotNull SlashCommandEventData data) {
        this.guildId = guildId;
        this.id = id;
        this.type = type;
        this.version = version;
        this.channelId = channelId;
        this.applicationId = applicationId;
        this.token = token;
        this.member = member;
        this.data = data;
    }

    @NotNull
    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(@NotNull String guildId) {
        this.guildId = guildId;
    }

    @NotNull
    public String getId() {
        return id;
    }

    public void setId(@NotNull String id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @NotNull
    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(@NotNull String channelId) {
        this.channelId = channelId;
    }

    @NotNull
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(@NotNull String applicationId) {
        this.applicationId = applicationId;
    }

    @NotNull
    public String getToken() {
        return token;
    }

    public void setToken(@NotNull String token) {
        this.token = token;
    }

    @NotNull
    public SlashCommandEventMember getMember() {
        return member;
    }

    public void setMember(@NotNull SlashCommandEventMember member) {
        this.member = member;
    }

    @NotNull
    public SlashCommandEventData getData() {
        return data;
    }

    public void setData(@NotNull SlashCommandEventData data) {
        this.data = data;
    }
}
