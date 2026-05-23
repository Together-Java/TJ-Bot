package org.togetherjava.tjbot.features.chatgpt.schema;

import java.util.List;
import java.util.Map;

/**
 * Top-level JSON schema describing the shape of a structured response from the OpenAI API.
 * <p>
 * Mirrors the {@code json_schema.schema} object that OpenAI's structured-outputs feature expects:
 * an object schema with declared {@code properties}, a {@code required} list, and the
 * {@code additionalProperties} flag (which must be {@code false} in strict mode).
 * <p>
 * Use {@link Property} (and its static factories) to build the {@code properties} map. Example:
 *
 * <pre>{@code
 * ResponseSchema schema = new ResponseSchema(Map.of("answer", Property.of(Type.STRING), "tags",
 *         Property.array(Property.of(Type.STRING))), List.of("answer", "tags"));
 * }</pre>
 *
 * @param type the JSON type — must be {@link Type#OBJECT} for a top-level schema
 * @param properties the fields of the response object, keyed by field name
 * @param required the names of fields the model must always include
 * @param additionalProperties whether undeclared fields are allowed; strict mode requires
 *        {@code false}
 */
public record ResponseSchema(Type type, Map<String, Property> properties, List<String> required,
        boolean additionalProperties) {

    /**
     * Creates a strict-mode object schema: {@code type=object}, {@code additionalProperties=false}.
     *
     * @param properties the fields of the response object, keyed by field name
     * @param required the names of fields the model must always include
     */
    public ResponseSchema(Map<String, Property> properties, List<String> required) {
        this(Type.OBJECT, properties, required, false);
    }
}
