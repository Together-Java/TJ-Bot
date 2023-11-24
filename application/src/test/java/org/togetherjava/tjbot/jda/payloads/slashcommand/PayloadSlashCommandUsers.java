package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import org.togetherjava.tjbot.jda.payloads.PayloadUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PayloadSlashCommandUsers {
    private Map<String, PayloadUser> idToUser;

    public PayloadSlashCommandUsers(Map<String, PayloadUser> idToUser) {
        this.idToUser = new HashMap<>(idToUser);
    }

    @JsonAnyGetter
    public Map<String, PayloadUser> getIdToUser() {
        return Collections.unmodifiableMap(idToUser);
    }

    @JsonAnySetter
    public void setIdToUser(Map<String, PayloadUser> idToUser) {
        this.idToUser = new HashMap<>(idToUser);
    }
}
