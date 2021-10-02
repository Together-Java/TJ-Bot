package org.togetherjava.tjbot.commands.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum ComponentIds {
    ;

    private static final CsvMapper CSV = new CsvMapper();

    public static @NotNull String generate(@NotNull String commandName,
            @NotNull List<String> elements) throws JsonProcessingException {
        return CSV.writerFor(ComponentId.class)
            .with(CSV.schemaFor(ComponentId.class))
            .writeValueAsString(new ComponentId(commandName, elements));
    }

    public static @NotNull ComponentId parse(@NotNull String componentId)
            throws JsonProcessingException {
        return CSV.readerFor(ComponentId.class)
            .with(CSV.schemaFor(ComponentId.class))
            .readValue(componentId);
    }
}
