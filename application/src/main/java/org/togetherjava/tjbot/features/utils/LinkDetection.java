package org.togetherjava.tjbot.features.utils;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class to detect links.
 */
public class LinkDetection {

    /**
     * Possible ways to filter a link.
     *
     * @see LinkDetection
     */
    public enum LinkFilter {
        /**
         * Filters links suppressed with {@literal <url>}.
         */
        SUPPRESSED,
        /**
         * Filters links that are not using http scheme.
         */
        NON_HTTP_SCHEME
    }

    private LinkDetection() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts all links from the given content.
     *
     * @param content the content to search through
     * @param filter the filters applied to the urls
     * @return a list of all found links, can be empty
     */
    public static List<String> extractLinks(String content, Set<LinkFilter> filter) {
        return new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect()
            .stream()
            .map(url -> toLink(url, filter))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * Checks whether the given content contains a link.
     *
     * @param content the content to search through
     * @return true if the content contains at least one link
     */
    public static boolean containsLink(String content) {
        return !(new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect().isEmpty());
    }

    private static Optional<String> toLink(Url url, Set<LinkFilter> filter) {
        String raw = url.getOriginalUrl();
        if (filter.contains(LinkFilter.SUPPRESSED) && raw.contains(">")) {
            // URL escapes, such as "<http://example.com>" should be skipped
            return Optional.empty();
        }
        // Not interested in other schemes, also to filter out matches without scheme.
        // It detects a lot of such false-positives in Java snippets
        if (filter.contains(LinkFilter.NON_HTTP_SCHEME) && !raw.startsWith("http")) {
            return Optional.empty();
        }

        String link = url.getFullUrl();

        if (link.endsWith(",") || link.endsWith(".")) {
            // Remove trailing punctuation
            link = link.substring(0, link.length() - 1);
        }

        return Optional.of(link);
    }

}
