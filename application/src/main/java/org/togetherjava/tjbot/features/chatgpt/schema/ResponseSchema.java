package org.togetherjava.tjbot.features.chatgpt.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 */
public class ResponseSchema {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Type type;
    private final Map<String, Property> properties;
    private final List<String> required;
    private final boolean additionalProperties;

    /**
     * Creates a fully-specified schema. Most callers should prefer
     * {@link #ResponseSchema(Map, List)}, which fixes {@code type} to {@link Type#OBJECT} and
     * {@code additionalProperties} to {@code false} as OpenAI's strict mode requires.
     *
     * @param type the JSON type — must be {@link Type#OBJECT} for a top-level schema
     * @param properties the fields of the response object, keyed by field name
     * @param required the names of fields the model must always include
     * @param additionalProperties whether undeclared fields are allowed; strict mode requires
     *        {@code false}
     */
    public ResponseSchema(Type type, Map<String, Property> properties, List<String> required,
            boolean additionalProperties) {
        this.type = type;
        this.properties = properties;
        this.required = required;
        this.additionalProperties = additionalProperties;
    }

    /**
     * Creates a strict-mode object schema: {@code type=object}, {@code additionalProperties=false}.
     *
     * @param properties the fields of the response object, keyed by field name
     * @param required the names of fields the model must always include
     */
    public ResponseSchema(Map<String, Property> properties, List<String> required) {
        this(Type.OBJECT, properties, required, false);
    }

    /**
     * Serializes this schema to its JSON representation, suitable for embedding as the
     * {@code schema} value of an OpenAI {@code response_format.json_schema} block.
     *
     * @return the JSON string form of this schema
     * @throws RuntimeException if Jackson fails to serialize (should not happen for valid input)
     */
    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the declared JSON type (always {@link Type#OBJECT} for a valid top-level schema)
     */
    public Type type() {
        return type;
    }

    /**
     * @return the field definitions of the response object, keyed by field name
     */
    public Map<String, Property> properties() {
        return properties;
    }

    /**
     * @return the names of fields the model must always include in its response
     */
    public List<String> required() {
        return required;
    }

    /**
     * @return whether fields beyond those declared in {@link #properties} are permitted
     */
    public boolean additionalProperties() {
        return additionalProperties;
    }
}
