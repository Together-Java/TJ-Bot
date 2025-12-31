package org.togetherjava.tjbot.features.utils;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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

    public static CompletableFuture<Boolean> isLinkBroken(String url) {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenApply(response -> response.statusCode() >= 400)
            .exceptionally(ignored -> true);
    }

    public static CompletableFuture<String> replaceDeadLinks(String text, String replacement) {
        Set<LinkFilter> filters = Set.of(LinkFilter.SUPPRESSED, LinkFilter.NON_HTTP_SCHEME);

        List<String> links = extractLinks(text, filters);

        if (links.isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }

        StringBuilder result = new StringBuilder(text);

        List<CompletableFuture<Void>> checks =
                links.stream().map(link -> isLinkBroken(link).thenAccept(isDead -> {
                    if (isDead) {
                        int index = result.indexOf(link);
                        if (index != -1) {
                            result.replace(index, index + link.length(), replacement);
                        }
                    }
                })).toList();

        return CompletableFuture.allOf(checks.toArray(new CompletableFuture[0]))
            .thenApply(v -> result.toString());
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
