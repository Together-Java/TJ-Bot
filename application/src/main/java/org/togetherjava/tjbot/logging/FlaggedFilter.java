package org.togetherjava.tjbot.logging;

import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * A custom Filter for Log4j2, which only lets an event pass through if a Logging Flag is set in the
 * environment. Intended to be used for local development for devs do not want to also run the
 * logviewer project. No errors in console or Log should appear, if the Flag is not set and the
 * logviewer is not running.
 */
@Plugin(name = "FlaggedFilter", category = Core.CATEGORY_NAME, elementType = Filter.ELEMENT_TYPE)
public class FlaggedFilter extends AbstractFilter {

    /**
     * The environment Variable that needs to bet set in order for this Filter to let events through
     */
    public static final String LOGGING_FLAG = "TJ_APPENDER";

    /**
     * Create a FlaggedFilter.
     *
     * @param onMatch The action to take on a match.
     * @param onMismatch The action to take on a mismatch.
     */
    public FlaggedFilter(Result onMatch, Result onMismatch) {
        super(onMatch, onMismatch);
    }

    /**
     * The actual filtering occurs here. If the Flag {@link #LOGGING_FLAG} is not set returns
     * {@link Result#DENY} so nothing goes through. If the Flag is set it returns
     * {@link Result#NEUTRAL} so other configured Filter still work.
     *
     * @param event The Event to log.
     * @return {@link Result#DENY} if the Flag is not set, else {@link Result#NEUTRAL}
     */
    @Override
    public Result filter(LogEvent event) {
        return isLoggingEnabled() ? Result.NEUTRAL : Result.DENY;
    }

    boolean isLoggingEnabled() {
        return System.getenv().containsKey(LOGGING_FLAG);
    }

    /**
     * Required by the Log4j2 - Plugin framework in order to create an instance of this Filter.
     *
     * @param onMatch The action to take on a match.
     * @param onMismatch The action to take on a mismatch.
     * @return The created FlaggedFilter.
     */
    @PluginFactory
    public static FlaggedFilter createFilter(
            @PluginAttribute(value = "onMatch", defaultString = "NEUTRAL") Result onMatch,

            @PluginAttribute(value = "onMismatch", defaultString = "DENY") Result onMismatch) {
        return new FlaggedFilter(onMatch, onMismatch);
    }
}
