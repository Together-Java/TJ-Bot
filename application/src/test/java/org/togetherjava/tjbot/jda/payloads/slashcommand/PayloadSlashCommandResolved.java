package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.Nullable;

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

    public @Nullable PayloadSlashCommandMembers getMembers() {
        return members;
    }

    public void setMembers(@Nullable PayloadSlashCommandMembers members) {
        this.members = members;
    }

    public @Nullable PayloadSlashCommandUsers getUsers() {
        return users;
    }

    public void setUsers(@Nullable PayloadSlashCommandUsers users) {
        this.users = users;
    }
}
