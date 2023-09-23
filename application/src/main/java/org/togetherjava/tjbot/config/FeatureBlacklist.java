package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Blacklist of features, use {@link FeatureBlacklist#isEnabled(T)} to test if a feature is enabled.
 * If a feature is blacklisted, it won't be enabled by the bot, and so will be ignored.
 * 
 * @param <T> the type of the feature identifier
 */
public class FeatureBlacklist<T> {
    private final Set<T> featureIdentifierBlacklist;
    private static final Logger logger = LoggerFactory.getLogger(FeatureBlacklist.class);

    /**
     * Creates a feature blacklist
     * 
     * @param featureIdentifierBlacklist a set of identifiers which are blacklisted
     */
    @JsonCreator
    public FeatureBlacklist(Set<T> featureIdentifierBlacklist) {
        this.featureIdentifierBlacklist = Set.copyOf(featureIdentifierBlacklist);
    }

    /**
     * Returns if a feature is enabled or not.
     * 
     * @param featureId the identifier of the feature
     * @return true if a feature is enabled, false otherwise
     */
    public boolean isEnabled(T featureId) {
        boolean isBlackListed = featureIdentifierBlacklist.contains(featureId);
        if (isBlackListed) {
            logger.info(String.format("%s is blacklisted 😥", featureId));
        }
        return !isBlackListed;
    }
}
