package org.togetherjava.tjbot.jda;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PayloadSlashCommandData {
    private String name;
    private String id;
    private int type;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PayloadSlashCommandOption> options;

    PayloadSlashCommandData(@NotNull String name, @NotNull String id, int type,
            @Nullable List<PayloadSlashCommandOption> options) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.options = options == null ? null : new ArrayList<>(options);
    }

    @NotNull
    public String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    @NotNull
    public String getId() {
        return id;
    }

    public void setId(@NotNull String id) {
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
}
