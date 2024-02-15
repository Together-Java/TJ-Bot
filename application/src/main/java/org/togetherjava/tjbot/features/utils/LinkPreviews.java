package org.togetherjava.tjbot.features.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Provides means to create previews of links. See
 * {@link LinkDetection#extractLinks(String, boolean, boolean)} and
 * {@link #createLinkPreviews(List)}.
 */
public final class LinkPreviews {
    private static final Logger logger = LoggerFactory.getLogger(LinkPreviews.class);

    private static final String IMAGE_CONTENT_TYPE_PREFIX = "image";
    private static final String IMAGE_META_NAME = "image";

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private LinkPreviews() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Attempts to create previews of all given links.
     * <p>
     * A link preview is a short representation of the links contents, for example a thumbnail with
     * a description.
     * <p>
     * The returned result does not necessarily contain a preview for all given links. Preview
     * creation can fail for various reasons, failed previews are omitted in the result.
     *
     * @param links the links to preview
     * @return a list of all previews created successfully, can be empty
     */
    public static CompletableFuture<List<LinkPreview>> createLinkPreviews(List<String> links) {
        if (links.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<Optional<LinkPreview>>> tasks = IntStream.range(0, links.size())
            .mapToObj(i -> createLinkPreview(links.get(i), i + ".png"))
            .toList();

        var allDoneTask = CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
        return allDoneTask.thenApply(any -> extractResults(tasks)).exceptionally(e -> {
            logger.error("Unknown error during link preview creation", e);
            return List.of();
        });
    }

    private static <T> List<T> extractResults(
            Collection<? extends CompletableFuture<Optional<T>>> tasks) {
        return tasks.stream()
            .filter(Predicate.not(CompletableFuture::isCompletedExceptionally))
            .map(CompletableFuture::join)
            .flatMap(Optional::stream)
            .toList();
    }

    private static CompletableFuture<Optional<LinkPreview>> createLinkPreview(String link,
            String attachmentName) {
        return readLinkContent(link).thenCompose(maybeContent -> {
            if (maybeContent.isEmpty()) {
                return noResult();
            }
            HttpContent content = maybeContent.orElseThrow();

            if (content.type.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
                return result(LinkPreview.ofThumbnail(attachmentName, content.dataStream));
            }

            if (content.type.startsWith("text/html")) {
                return parseWebsite(link, attachmentName, content.dataStream);
            }
            return noResult();
        }).orTimeout(10, TimeUnit.SECONDS).exceptionally(e -> {
            logger.warn("Failed to create link preview for {}", link, e);
            return Optional.empty();
        });
    }

    private static CompletableFuture<Optional<HttpContent>> readLinkContent(String link) {
        URI linkAsUri;
        try {
            linkAsUri = URI.create(link);
        } catch (IllegalArgumentException e) {
            logger.warn("Attempted to create a preview for {}, but the URL is invalid.", link, e);
            return noResult();
        }

        HttpRequest request = HttpRequest.newBuilder(linkAsUri).build();
        CompletableFuture<HttpResponse<InputStream>> task =
                CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

        return task.thenApply(response -> {
            int statusCode = response.statusCode();
            if (statusCode < HttpURLConnection.HTTP_OK
                    || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                logger.warn("Attempted to create a preview for {}, but the site returned code {}.",
                        link, statusCode);
                return Optional.empty();
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return Optional.of(new HttpContent(contentType, response.body()));
        });
    }

    private record HttpContent(String type, InputStream dataStream) {
    }

    private static CompletableFuture<Optional<LinkPreview>> parseWebsite(String link,
            String attachmentName, InputStream websiteContent) {
        Document doc;
        try {
            doc = Jsoup.parse(websiteContent, null, link);
        } catch (IOException e) {
            logger.warn("Attempted to create a preview for {}, but the content is invalid.", link,
                    e);
            return noResult();
        }

        String title = parseOpenGraphTwitterMeta(doc, "title", doc.title()).orElse(null);
        String description =
                parseOpenGraphTwitterMeta(doc, "description", doc.title()).orElse(null);

        LinkPreview textPreview = LinkPreview.ofText(title, link, description);

        String image = parseOpenGraphTwitterMeta(doc, IMAGE_META_NAME, null).orElse(null);
        if (image == null) {
            return result(textPreview);
        }

        return readLinkContent(image).thenCompose(maybeContent -> {
            if (maybeContent.isEmpty()) {
                return result(textPreview);
            }
            HttpContent content = maybeContent.orElseThrow();

            if (!content.type.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
                return result(textPreview);
            }

            return result(textPreview.withThumbnail(attachmentName, content.dataStream));
        });
    }

    private static Optional<String> parseOpenGraphTwitterMeta(Document doc, String metaProperty,
            @Nullable String fallback) {
        String value = parseMetaProperty(doc, "og:" + metaProperty)
            .or(() -> parseMetaProperty(doc, "twitter:" + metaProperty))
            .orElse(fallback);

        if (value == null) {
            return Optional.empty();
        }
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<String> parseMetaProperty(Document doc, String metaProperty) {
        return selectFirstMetaTag(doc, "property", metaProperty)
            .or(() -> selectFirstMetaTag(doc, "name", metaProperty))
            .filter(Predicate.not(String::isBlank));
    }

    private static Optional<String> selectFirstMetaTag(Document doc, String key, String value) {
        return Optional.ofNullable(doc.selectFirst("meta[%s=%s]".formatted(key, value)))
            .map(element -> element.attr("content"));
    }

    private static <T> CompletableFuture<Optional<T>> noResult() {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    private static <T> CompletableFuture<Optional<T>> result(T content) {
        return CompletableFuture.completedFuture(Optional.of(content));
    }
}
