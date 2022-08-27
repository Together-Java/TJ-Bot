package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.togetherjava.tjbot.jda.payloads.PayloadMember;

import javax.annotation.Nonnull;

public final class PayloadSlashCommand {
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
    private PayloadMember member;
    private PayloadSlashCommandData data;

    public PayloadSlashCommand(String guildId, String id, int type, int version, String channelId,
            String applicationId, String token, PayloadMember member,
            PayloadSlashCommandData data) {
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

    @Nonnull
    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    @Nonnull
    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    @Nonnull
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    @Nonnull
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Nonnull
    public PayloadMember getMember() {
        return member;
    }

    public void setMember(PayloadMember member) {
        this.member = member;
    }

    @Nonnull
    public PayloadSlashCommandData getData() {
        return data;
    }

    public void setData(PayloadSlashCommandData data) {
        this.data = data;
    }
}
