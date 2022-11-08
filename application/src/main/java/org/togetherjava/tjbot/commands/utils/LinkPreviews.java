package org.togetherjava.tjbot.commands.utils;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public final class LinkPreviews {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private LinkPreviews() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<String> extractLinks(String content) {
        return new UrlDetector(content, UrlDetectorOptions.BRACKET_MATCH).detect()
            .stream()
            .map(Url::getFullUrl)
            .toList();
    }

    public static CompletableFuture<List<LinkPreview>> createLinkPreviews(List<String> links) {
        if (links.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // TODO This stuff needs some polishing, barely readable
        List<CompletableFuture<Optional<LinkPreview>>> tasks = IntStream.range(0, links.size())
            .mapToObj(i -> createLinkPreview(links.get(i), i + ".png"))
            .toList();

        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
            .thenApply(any -> tasks.stream()
                .filter(Predicate.not(CompletableFuture::isCompletedExceptionally))
                .map(CompletableFuture::join)
                .flatMap(Optional::stream)
                .toList());
    }

    private static CompletableFuture<Optional<LinkPreview>> createLinkPreview(String link,
            String attachmentName) {
        URI linkAsUri;
        try {
            linkAsUri = URI.create(link);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HttpRequest request = HttpRequest.newBuilder(linkAsUri).build();
        CompletableFuture<HttpResponse<InputStream>> task =
                CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

        return task.thenCompose(response -> {
            int statusCode = response.statusCode();
            if (statusCode < HttpURLConnection.HTTP_OK
                    || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (isResponseOfType(response, "image")) {
                return CompletableFuture.completedFuture(
                        Optional.of(LinkPreview.ofThumbnail(attachmentName, response.body())));
            }
            if (isResponseOfType(response, "text/html")) {
                return parseWebsite(link, attachmentName, response.body());
            }

            return CompletableFuture.completedFuture(Optional.empty());
        });
    }

    private static boolean isResponseOfType(HttpResponse<?> response, String type) {
        return response.headers()
            .firstValue("Content-Type")
            .filter(contentType -> contentType.startsWith(type))
            .isPresent();
    }

    private static CompletableFuture<Optional<LinkPreview>> parseWebsite(String link,
            String attachmentName, InputStream websiteContent) {
        Document doc;
        try {
            doc = Jsoup.parse(websiteContent, null, link);
        } catch (IOException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String title = parseOpenGraphTwitterMeta(doc, "title", doc.title()).orElse(null);
        String description =
                parseOpenGraphTwitterMeta(doc, "description", doc.title()).orElse(null);
        String image = parseOpenGraphMeta(doc, "image").orElse(null);

        if (image == null) {
            // TODO Can still do something
            return CompletableFuture.completedFuture(Optional.empty());
        }

        // TODO Massive duplication
        URI imageAsUri;
        try {
            imageAsUri = URI.create(image);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HttpRequest request = HttpRequest.newBuilder(imageAsUri).build();
        CompletableFuture<HttpResponse<InputStream>> task =
                CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

        return task.thenCompose(response -> {
            int statusCode = response.statusCode();
            if (statusCode < HttpURLConnection.HTTP_OK
                    || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (!isResponseOfType(response, "image")) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return CompletableFuture.completedFuture(Optional.of(LinkPreview.ofContents(title, link,
                    description, attachmentName, response.body())));
        });
    }

    private static Optional<String> parseOpenGraphTwitterMeta(Document doc, String metaProperty,
            @Nullable String fallback) {
        String value = Optional
            .ofNullable(doc.selectFirst("meta[property=og:%s]".formatted(metaProperty)))
            .or(() -> Optional
                .ofNullable(doc.selectFirst("meta[property=twitter:%s".formatted(metaProperty))))
            .map(element -> element.attr("content"))
            .orElse(fallback);
        if (value == null) {
            return Optional.empty();
        }
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<String> parseOpenGraphMeta(Document doc, String metaProperty) {
        return Optional.ofNullable(doc.selectFirst("meta[property=og:%s]".formatted(metaProperty)))
            .map(element -> element.attr("content"))
            .filter(Predicate.not(String::isBlank));
    }
}
