package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.togetherjava.tjbot.jda.payloads.PayloadChannel;
import org.togetherjava.tjbot.jda.payloads.PayloadMember;

public final class PayloadSlashCommand {
    @JsonProperty("guild_id")
    private String guildId;
    private String id;
    private int type;
    private int version;
    @JsonProperty("application_id")
    private String applicationId;
    private String token;
    private PayloadMember member;
    private PayloadChannel channel;
    private PayloadSlashCommandData data;

    public PayloadSlashCommand(String guildId, String id, int type, int version,
            String applicationId, String token, PayloadMember member, PayloadChannel channel,
            PayloadSlashCommandData data) {
        this.guildId = guildId;
        this.id = id;
        this.type = type;
        this.version = version;
        this.applicationId = applicationId;
        this.token = token;
        this.member = member;
        this.channel = channel;
        this.data = data;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

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

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public PayloadMember getMember() {
        return member;
    }

    public void setMember(PayloadMember member) {
        this.member = member;
    }

    public PayloadChannel getChannel() {
        return channel;
    }

    public void setChannel(PayloadChannel channel) {
        this.channel = channel;
    }

    public PayloadSlashCommandData getData() {
        return data;
    }

    public void setData(PayloadSlashCommandData data) {
        this.data = data;
    }

}
