package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PayloadSlashCommandData {
    private String name;
    private String id;
    private int type;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PayloadSlashCommandOption> options;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PayloadSlashCommandResolved resolved;

    public PayloadSlashCommandData(String name, String id, int type,
            @Nullable List<PayloadSlashCommandOption> options,
            @Nullable PayloadSlashCommandResolved resolved) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.options = options == null ? null : new ArrayList<>(options);
        this.resolved = resolved;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    @Nullable
    public List<PayloadSlashCommandOption> getOptions() {
        return options == null ? null : Collections.unmodifiableList(options);
    }

    public void setOptions(@Nullable List<PayloadSlashCommandOption> options) {
        this.options = options == null ? null : new ArrayList<>(options);
    }

    @Nullable
    public PayloadSlashCommandResolved getResolved() {
        return resolved;
    }

    public void setResolved(@Nullable PayloadSlashCommandResolved resolved) {
        this.resolved = resolved;
    }
}
