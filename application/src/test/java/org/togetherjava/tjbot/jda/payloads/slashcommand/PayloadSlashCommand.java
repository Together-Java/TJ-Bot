package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.interactions.IntegrationOwners;
import net.dv8tion.jda.api.interactions.InteractionContextType;

import org.togetherjava.tjbot.jda.payloads.PayloadChannel;
import org.togetherjava.tjbot.jda.payloads.PayloadGuild;
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
    private PayloadGuild guild;
    private PayloadSlashCommandData data;
    @JsonProperty("channel_id")
    private long channelId;
    private InteractionContextType context;
    @JsonProperty("authorizing_integration_owners")
    private IntegrationOwners integrationOwners;

    public PayloadSlashCommand(String guildId, String id, int type, int version,
            String applicationId, String token, PayloadMember member, PayloadChannel channel,
            PayloadGuild guild, PayloadSlashCommandData data, long channelId,
            InteractionContextType context, IntegrationOwners integrationOwners) {
        this.guildId = guildId;
        this.id = id;
        this.type = type;
        this.version = version;
        this.applicationId = applicationId;
        this.token = token;
        this.member = member;
        this.channel = channel;
        this.guild = guild;
        this.data = data;
        this.channelId = channelId;
        this.context = context;
        this.integrationOwners = integrationOwners;
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

    public PayloadGuild getGuild() {
        return guild;
    }

    public void setGuild(PayloadGuild guild) {
        this.guild = guild;
    }

    public PayloadSlashCommandData getData() {
        return data;
    }

    public void setData(PayloadSlashCommandData data) {
        this.data = data;
    }

    public long getChannelId() {
        return channelId;
    }

    public void setChannelId(long channelId) {
        this.channelId = channelId;
    }

    public InteractionContextType getContext() {
        return context;
    }

    public void setContext(InteractionContextType context) {
        this.context = context;
    }

    public IntegrationOwners getIntegrationOwners() {
        return integrationOwners;
    }

    public void setIntegrationOwners(IntegrationOwners integrationOwners) {
        this.integrationOwners = integrationOwners;
    }

}
