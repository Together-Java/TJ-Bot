package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Configuration of the feature blacklist, any feature present here will be disabled.
 * <p>
 * The argument {@code "normal"} expects a list of fully qualified class names of
 * {@link org.togetherjava.tjbot.features.Feature}s, for example:
 * 
 * <pre>
 * {@code
 * "normal": [
 *   "org.togetherjava.tjbot.features.basic.PingCommand",
 *   "org.togetherjava.tjbot.features.tophelper.TopHelpersAssignmentRoutine"
 * ]}
 * </pre>
 * <p>
 * The argument {@code "special"} is a special set of predefined strings that disable specific
 * features. Currently available are:
 * <ul>
 * <li>{@code "org.togetherjava.tjbot.features.code.FormatCodeCommand"}</li>
 * <li>{@code "org.togetherjava.tjbot.features.code.EvalCodeCommand"}</li>
 * </ul>
 *
 * @param normal the normal features, which are present in
 *        {@link org.togetherjava.tjbot.features.Features}
 * @param special the special features, which require special code
 */
public record FeatureBlacklistConfig(
        @JsonProperty(value = "normal", required = true) FeatureBlacklist<Class<?>> normal,
        @JsonProperty(value = "special", required = true) FeatureBlacklist<String> special) {

    /**
     * Creates a FeatureBlacklistConfig.
     * 
     * @param normal the list of normal features, must be not null
     * @param special the list of special features, must be not null
     */
    public FeatureBlacklistConfig {
        Objects.requireNonNull(normal);
        Objects.requireNonNull(special);
    }
}
