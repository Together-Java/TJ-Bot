package org.togetherjava.tjbot.features.chatgpt.schema;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The JSON Schema primitive types supported by OpenAI's structured outputs. Each constant
 * serializes to its lowercase form (e.g. {@code STRING} → {@code "string"}) via {@link #jsonValue},
 * matching the JSON Schema specification.
 */
public enum Type {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    OBJECT,
    ARRAY,
    NULL;

    /**
     * Returns the lowercase JSON representation of this type. Used by Jackson via
     * {@link JsonValue}, so the enum serializes to {@code "string"} rather than {@code "STRING"}.
     *
     * @return the JSON Schema type name in lowercase
     */
    @JsonValue
    public String jsonValue() {
        return name().toLowerCase();
    }
}
