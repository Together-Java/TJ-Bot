package org.togetherjava.tjbot.jda.payloads.slashcommand;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PayloadSlashCommandOption {
    private String name;
    private int type;
    private String value;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PayloadSlashCommandOption> options;

    public PayloadSlashCommandOption(String name, int type, @Nullable String value,
            @Nullable List<PayloadSlashCommandOption> options) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.options = options == null ? null : new ArrayList<>(options);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    public void setValue(@Nullable String value) {
        this.value = value;
    }

    @Nullable
    public List<PayloadSlashCommandOption> getOptions() {
        return options == null ? null : Collections.unmodifiableList(options);
    }

    public void setOptions(@Nullable List<PayloadSlashCommandOption> options) {
        this.options = options == null ? null : new ArrayList<>(options);
    }
}
