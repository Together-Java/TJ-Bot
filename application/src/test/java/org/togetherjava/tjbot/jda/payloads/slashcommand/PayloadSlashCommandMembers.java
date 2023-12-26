package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import org.togetherjava.tjbot.jda.payloads.PayloadMember;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PayloadSlashCommandMembers {
    private Map<String, PayloadMember> idToMember;

    public PayloadSlashCommandMembers(Map<String, PayloadMember> idToMember) {
        this.idToMember = new HashMap<>(idToMember);
    }

    @JsonAnyGetter
    public Map<String, PayloadMember> getIdToMember() {
        return Collections.unmodifiableMap(idToMember);
    }

    @JsonAnySetter
    public void setIdToMember(Map<String, PayloadMember> idToMember) {
        this.idToMember = new HashMap<>(idToMember);
    }
}
