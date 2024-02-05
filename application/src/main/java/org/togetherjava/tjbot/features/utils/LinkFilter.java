package org.togetherjava.tjbot.features.utils;

/**
 * Possible ways to filter a link.
 * 
 * @see LinkDetections
 */
public enum LinkFilter {
    /**
     * Filters links suppressed with {@literal <url>}.
     */
    SUPPRESSED,
    /**
     * Filters links that are not using http scheme.
     */
    NON_HTTP_SCHEME;
}
