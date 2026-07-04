package org.togetherjava.tjbot.features.chatgpt.tools;

/**
 * Coarse value types a {@link AiToolParameter} may declare. Translated to the corresponding JSON
 * schema {@code type} keyword so OpenAI can validate the model's tool-call arguments.
 */
public enum AiToolParameterType {
    STRING,
    INT,
    DOUBLE,
    FLOAT,
    LONG,
    BOOLEAN;

    /**
     * Maps this Java-flavoured type to the JSON schema type keyword OpenAI expects.
     *
     * @return one of {@code "string"}, {@code "integer"}, {@code "number"}, or {@code "boolean"}
     */
    public String toJsonSchemaType() {
        return switch (this) {
            case STRING -> "string";
            case INT, LONG -> "integer";
            case DOUBLE, FLOAT -> "number";
            case BOOLEAN -> "boolean";
        };
    }
}
