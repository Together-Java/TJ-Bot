package org.togetherjava.tjbot.features.utils;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Utility methods for working with links inside arbitrary text.
 *
 * <p>
 * This class can:
 * <ul>
 * <li>Extract HTTP(S) links from text</li>
 * <li>Check whether a link is reachable via HTTP</li>
 * <li>Replace broken links asynchronously</li>
 * </ul>
 *
 * <p>
 * It is intentionally stateless and uses asynchronous HTTP requests to avoid blocking calling
 * threads.
 */

public class LinkDetection {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * Default filters applied when extracting links from text.
     *
     * <p>
     * Links to intentionally ignore in order to reduce false positives when scanning chat messages
     * or source-code snippets.
     */

    private static final Set<LinkFilter> DEFAULT_FILTERS =
            Set.of(LinkFilter.SUPPRESSED, LinkFilter.NON_HTTP_SCHEME);

    /**
     * Filters that control which detected URLs are returned by {@link #extractLinks}.
     */
    public enum LinkFilter {
        /**
         * Ignores URLs that are wrapped in angle brackets, e.g. {@code <https://example.com>}.
         *
         * <p>
         * Such links are often intentionally suppressed in chat platforms.
         */
        SUPPRESSED,
        /**
         * Ignores URLs that do not use the HTTP or HTTPS scheme.
         *
         * <p>
         * This helps avoid false positives such as {@code ftp://}, {@code file://}, or scheme-less
         * matches.
         */
        NON_HTTP_SCHEME
    }

    private LinkDetection() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts links from the given text.
     *
     * <p>
     * The text is scanned using a URL detector, then filtered and normalized according to the
     * provided {@link LinkFilter}s.
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     * Set<LinkFilter> filters = Set.of(LinkFilter.SUPPRESSED, LinkFilter.NON_HTTP_SCHEME);
     * extractLinks("Visit https://example.com and <ftp://skip.me>", filters)
     * // returns ["https://example.com"]
     * }</pre>
     *
     * @param content the text to scan for links
     * @param filter a set of filters controlling which detected links are returned
     * @return a list of extracted links in the order they appear in the text
     */

    public static List<String> extractLinks(String content, Set<LinkFilter> filter) {
        return new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect()
            .stream()
            .map(url -> toLink(url, filter))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * Extracts links from the given text using default filters.
     *
     * <p>
     * This is a convenience method that uses {@link #DEFAULT_FILTERS}.
     *
     * @param content the text to scan for links
     * @return a list of extracted links in the order they appear in the text
     * @see #extractLinks(String, Set)
     */
    public static List<String> extractLinks(String content) {
        return extractLinks(content, DEFAULT_FILTERS);
    }

    /**
     * Checks whether the given text contains at least one detectable URL.
     *
     * <p>
     * This method performs a lightweight detection only and does not apply any {@link LinkFilter}s.
     *
     * @param content the text to scan
     * @return {@code true} if at least one URL-like pattern is detected
     */

    public static boolean containsLink(String content) {
        return !(new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect().isEmpty());
    }

    /**
     * Asynchronously checks whether a URL is considered broken.
     *
     * <p>
     * A link is considered broken if:
     * <ul>
     * <li>The URL is malformed or unreachable</li>
     * <li>The HTTP request fails with an exception</li>
     * <li>The response status code is 4xx (client error) or 5xx (server error)</li>
     * </ul>
     *
     * <p>
     * Successful responses (2xx) and redirects (3xx) are considered valid links. The response body
     * is never inspected.
     *
     * @param url the URL to check
     * @return a {@code CompletableFuture} completing with {@code true} if the link is broken,
     *         {@code false} otherwise
     */

    public static CompletableFuture<Boolean> isLinkBroken(String url) {
        // Try HEAD request first (cheap and fast)
        HttpRequest headRequest = HttpRequest.newBuilder(URI.create(url))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();

        return HTTP_CLIENT.sendAsync(headRequest, HttpResponse.BodyHandlers.discarding())
            .thenApply(response -> {
                int status = response.statusCode();
                // 2xx and 3xx are success, 4xx and 5xx are errors
                return status >= 400;
            })
            .exceptionally(_ -> true)
            .thenCompose(result -> {
                if (!Boolean.TRUE.equals(result)) {
                    return CompletableFuture.completedFuture(false);
                }
                // If HEAD fails, fall back to GET request (some servers don't support HEAD)
                HttpRequest fallbackGetRequest =
                        HttpRequest.newBuilder(URI.create(url)).GET().build();
                return HTTP_CLIENT
                    .sendAsync(fallbackGetRequest, HttpResponse.BodyHandlers.discarding())
                    .thenApply(resp -> resp.statusCode() >= 400)
                    .exceptionally(_ -> true);
            });
    }

    /**
     * Replaces all broken links in the given text.
     *
     * <p>
     * Each detected link is checked asynchronously using {@link #isLinkBroken(String)}. Only links
     * confirmed as broken are replaced. Duplicate URLs are checked only once and all occurrences
     * are replaced if found to be broken.
     *
     * <p>
     * This method does not block - all link checks are performed asynchronously and combined into a
     * single {@code CompletableFuture}.
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     * replaceBrokenLinks("""
     *           Test
     *           http://deadlink/1
     *           http://workinglink/1
     *         """, "(broken link)")
     * }</pre>
     *
     * <p>
     * Results in:
     *
     * <pre>{@code
     * Test
     * (broken link)
     * http://workinglink/1
     * }</pre>
     *
     * @param text the input text containing URLs
     * @param replacement the string used to replace broken links
     * @return a {@code CompletableFuture} that completes with the modified text, or the original
     *         text if no broken links were found
     */


    public static CompletableFuture<String> replaceBrokenLinks(String text, String replacement) {
        List<String> links = extractLinks(text, DEFAULT_FILTERS);

        if (links.isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }

        List<CompletableFuture<String>> brokenLinkFutures = links.stream()
            .distinct()
            .map(link -> isLinkBroken(link).thenApply(isBroken -> isBroken ? link : null))
            .toList();

        return CompletableFuture.allOf(brokenLinkFutures.toArray(new CompletableFuture[0]))
            .thenApply(_ -> brokenLinkFutures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList())
            .thenApply(brokenLinks -> {
                String result = text;
                for (String brokenLink : brokenLinks) {
                    result = result.replace(brokenLink, replacement);
                }
                return result;
            });
    }

    /**
     * Converts a detected {@link Url} into a normalized link string.
     *
     * <p>
     * Applies the provided {@link LinkFilter}s. Additionally removes trailing punctuation such as
     * commas or periods from the detected URL.
     *
     * @param url the detected URL
     * @param filter active link filters to apply
     * @return an {@link Optional} containing the normalized link, or {@code Optional.empty()} if
     *         the link should be filtered out
     */

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
