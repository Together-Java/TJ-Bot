package org.togetherjava.tjbot.features.chatgpt.schema;

import java.util.List;
import java.util.Map;

/**
 * Represents a single property in an OpenAI JSON schema, used to describe the shape of one field
 * inside a {@link ResponseSchema}.
 * <p>
 * The hierarchy is sealed and mirrors the JSON Schema specification supported by OpenAI's
 * structured outputs:
 * <ul>
 * <li>{@link Primitive} – scalar values ({@code string}, {@code number}, {@code integer},
 * {@code boolean}, {@code null}).</li>
 * <li>{@link ArrayProperty} - an array whose elements conform to a nested {@link Property}.</li>
 * <li>{@link ObjectProperty} - a nested object with its own {@code properties} and {@code required}
 * list.</li>
 * </ul>
 * Prefer the static factory methods ({@link #of}, {@link #array}, {@link #object}) for readable
 * construction.
 *
 * @see ResponseSchema
 * @see <a href="https://platform.openai.com/docs/guides/structured-outputs">OpenAI Structured
 *      Outputs</a>
 */
public sealed interface Property
        permits Property.Primitive, Property.ArrayProperty, Property.ObjectProperty {

    /**
     * The JSON schema {@link Type} of this property.
     *
     * @return the type this property declares
     */
    Type type();

    /**
     * Creates a primitive property of the given scalar type.
     *
     * @param type a scalar type such as {@link Type#STRING} or {@link Type#INTEGER}
     * @return a new {@link Primitive} property
     */
    static Primitive of(Type type) {
        return new Primitive(type);
    }

    /**
     * Creates an array property whose elements conform to the given item schema.
     *
     * @param items the schema each element must satisfy
     * @return a new {@link ArrayProperty} of type {@link Type#ARRAY}
     */
    static ArrayProperty array(Property items) {
        return new ArrayProperty(items);
    }

    /**
     * Creates a nested object property with {@code additionalProperties} disabled, matching
     * OpenAI's strict-mode requirement.
     *
     * @param properties the fields of the nested object, keyed by field name
     * @param required the names of fields that must be present
     * @return a new {@link ObjectProperty} of type {@link Type#OBJECT}
     */
    static ObjectProperty object(Map<String, Property> properties, List<String> required) {
        return new ObjectProperty(properties, required, false);
    }

    /**
     * A scalar property - anything that isn't an object or array.
     *
     * @param type the scalar JSON type
     */
    record Primitive(Type type) implements Property {
    }

    /**
     * An array property describing a list of elements that all match {@link #items}.
     *
     * @param type always {@link Type#ARRAY}
     * @param items the schema each element must satisfy
     */
    record ArrayProperty(Type type, Property items) implements Property {
        /**
         * Convenience constructor that fixes {@link #type} to {@link Type#ARRAY}.
         *
         * @param items the schema each element must satisfy
         */
        public ArrayProperty(Property items) {
            this(Type.ARRAY, items);
        }
    }

    /**
     * A nested object property with its own field definitions. Mirrors the top-level
     * {@link ResponseSchema} structure, allowing arbitrarily deep nesting.
     *
     * @param type always {@link Type#OBJECT}
     * @param properties the fields of the nested object, keyed by field name
     * @param required the names of fields that must be present
     * @param additionalProperties whether fields beyond those declared in {@code properties} are
     *        allowed; OpenAI's strict mode requires {@code false}
     */
    record ObjectProperty(Type type, Map<String, Property> properties, List<String> required,
            boolean additionalProperties) implements Property {
        /**
         * Convenience constructor that fixes {@link #type} to {@link Type#OBJECT}.
         *
         * @param properties the fields of the nested object
         * @param required the names of fields that must be present
         * @param additionalProperties whether undeclared fields are allowed
         */
        public ObjectProperty(Map<String, Property> properties, List<String> required,
                boolean additionalProperties) {
            this(Type.OBJECT, properties, required, additionalProperties);
        }
    }
}
