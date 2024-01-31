package org.togetherjava.tjbot.features.utils;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

import java.util.List;
import java.util.Optional;

/**
 * Utility class to detect links.
 */
public class LinkDetections {

    private LinkDetections() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts all links from the given content, without any filters.
     *
     * @param content the content to search through
     * @return a list of all found links, can be empty
     */
    public static List<String> extractLinks(String content) {
        return extractLinks(content, false, false);
    }

    /**
     * Extracts all links from the given content.
     *
     * @param content the content to search through
     * @param filterSuppressed filters links suppressed with {@literal <url>}
     * @param filterNonHttpSchemes filters links that are not using http scheme
     * @return a list of all found links, can be empty
     */
    public static List<String> extractLinks(String content, boolean filterSuppressed, boolean filterNonHttpSchemes) {
        return new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect()
                .stream()
                .map(url -> toLink(url, filterSuppressed, filterNonHttpSchemes))
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
        return new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect().isEmpty();
    }

    private static Optional<String> toLink(Url url, boolean filterSuppressed, boolean filterNonHttpSchemes) {
        String raw = url.getOriginalUrl();
        if (filterSuppressed && raw.contains(">")) {
            // URL escapes, such as "<http://example.com>" should be skipped
            return Optional.empty();
        }
        // Not interested in other schemes, also to filter out matches without scheme.
        // It detects a lot of such false-positives in Java snippets
        if (filterNonHttpSchemes && !raw.startsWith("http")) {
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
