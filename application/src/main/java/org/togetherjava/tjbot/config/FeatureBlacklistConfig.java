package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Configuration of the feature blacklist, any feature present here will be disabled.
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
