package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.jda.payloads.PayloadUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PayloadSlashCommandUsers {
    private Map<String, PayloadUser> idToUser;

    public PayloadSlashCommandUsers(@NotNull Map<String, PayloadUser> idToUser) {
        this.idToUser = new HashMap<>(idToUser);
    }

    @JsonAnyGetter
    public @NotNull Map<String, PayloadUser> getIdToUser() {
        return Collections.unmodifiableMap(idToUser);
    }

    @JsonAnySetter
    public void setIdToUser(@NotNull Map<String, PayloadUser> idToUser) {
        this.idToUser = new HashMap<>(idToUser);
    }
}
