package org.togetherjava.tjbot.features.utils;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class to detect links.
 */
public class LinkDetection {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger logger = LoggerFactory.getLogger(LinkDetection.class);

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

    public static CompletableFuture<Boolean> isLinkDead(String url) {
        if (url == null || url.trim().isEmpty()) {
            // Treat null/empty links as dead
            return CompletableFuture.completedFuture(true);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();

            CompletableFuture<Boolean> linkStatusFuture =
                    client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .thenApply(HttpResponse::statusCode)
                        .thenApply(statusCode -> {
                            // Only links between 2xx-3xx are considered valid, therefore not dead.
                            if ((statusCode >= 200 && statusCode <= 299)
                                    || (statusCode >= 300 && statusCode <= 399)) {
                                return false;
                            }
                            return true;
                        })
                        .exceptionally(throwable -> {
                            logger.error(
                                    "Error validating URL " + url + ": " + throwable.getMessage());
                            return true;
                        });

            return linkStatusFuture;

        } catch (URISyntaxException e) {
            logger.error("Invalid URL " + url + ": " + e.getMessage());
            return CompletableFuture.completedFuture(true);
        }
    }

    public static CompletableFuture<String> replaceDeadLinks(String text, String replacement) {
        List<String> links =
                extractLinks(text, Set.of(LinkFilter.SUPPRESSED, LinkFilter.NON_HTTP_SCHEME));
        if (links.isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }

        // Manually figure out (start,end) indices for each link within text for later replacement
        List<Integer> startIndices = new ArrayList<>();
        List<Integer> endIndices = new ArrayList<>();
        int fromIndex = 0;
        for (String link : links) {
            int startIndex = text.indexOf(link, fromIndex);
            int endIndex = startIndex + link.length();
            startIndices.add(startIndex);
            endIndices.add(endIndex);

            fromIndex = endIndex;
        }

        List<CompletableFuture<Boolean>> futures =
                links.stream().map(link -> isLinkDead(link)).toList();

        CompletableFuture<Void> futuresResults =
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));

        // Replace all dead links within text w/ the given replacement string
        return futuresResults.thenApply(_ -> {
            StringBuilder modifiedText = new StringBuilder(text);
            for (int i = startIndices.size() - 1; i >= 0; i--) {
                boolean isDead = futures.get(i).join();
                if (isDead) {
                    modifiedText.replace(startIndices.get(i), endIndices.get(i), replacement);
                }
            }
            return modifiedText.toString();
        }).exceptionally(throwable -> {
            logger.error("Error replacing broken links in text {" + text + "}: "
                    + throwable.getMessage());
            return text;
        });
    }
}
