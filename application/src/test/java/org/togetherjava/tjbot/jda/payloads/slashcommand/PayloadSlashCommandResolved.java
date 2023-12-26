package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

public final class PayloadSlashCommandResolved {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PayloadSlashCommandMembers members;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PayloadSlashCommandUsers users;

    public PayloadSlashCommandResolved(@Nullable PayloadSlashCommandMembers members,
            @Nullable PayloadSlashCommandUsers users) {
        this.members = members;
        this.users = users;
    }

    @Nullable
    public PayloadSlashCommandMembers getMembers() {
        return members;
    }

    public void setMembers(@Nullable PayloadSlashCommandMembers members) {
        this.members = members;
    }

    @Nullable
    public PayloadSlashCommandUsers getUsers() {
        return users;
    }

    public void setUsers(@Nullable PayloadSlashCommandUsers users) {
        this.users = users;
    }
}
