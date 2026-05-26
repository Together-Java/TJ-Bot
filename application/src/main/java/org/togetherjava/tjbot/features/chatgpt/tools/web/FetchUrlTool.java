package org.togetherjava.tjbot.features.chatgpt.tools.web;

import org.jspecify.annotations.Nullable;

import org.togetherjava.tjbot.features.chatgpt.tools.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example tool: fetches an http(s) URL and returns the page as clean extracted text. Useful for the
 * model to read documentation, articles, or other public web pages.
 */
public final class FetchUrlTool implements AiTool<FetchUrlResult> {

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_LENGTH = 10_000;
    private static final int MAX_MAX_LENGTH = 100_000;

    private static final String URL_PARAM = "url";
    private static final String MAX_LENGTH_PARAM = "max_length";

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_PATTERN =
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN =
            Pattern.compile("<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    @Override
    public String name() {
        return "fetch_url";
    }

    @Override
    public String description() {
        return "Fetch a URL and return the page content as clean extracted text. "
                + "Useful for reading documentation or articles.";
    }

    @Override
    public List<AiToolParameter> parameters() {
        return List.of(
                new AiToolParameter(URL_PARAM, "The URL to fetch", AiToolParameterType.STRING,
                        true),
                new AiToolParameter(MAX_LENGTH_PARAM,
                        "Optional maximum characters of extracted text, up to " + MAX_MAX_LENGTH
                                + ". Defaults to " + DEFAULT_MAX_LENGTH + ".",
                        AiToolParameterType.INT, false));
    }

    @Override
    public AiToolResult<FetchUrlResult> run(Map<String, String> arguments) {
        String url = arguments.get(URL_PARAM);
        if (url == null || url.isBlank()) {
            return AiToolResult.failed("Missing required parameter: " + URL_PARAM);
        }

        Integer maxLength = parsePositiveInt(arguments.get(MAX_LENGTH_PARAM));
        if (maxLength == null || maxLength > MAX_MAX_LENGTH) {
            return AiToolResult
                .failed(MAX_LENGTH_PARAM + " must be a positive integer up to " + MAX_MAX_LENGTH);
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return AiToolResult.failed("Invalid URL: " + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return AiToolResult.failed("URL scheme must be http or https");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; TJ-Bot/1.0)")
                .GET()
                .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            String title = extractTitle(body);
            String text = htmlToText(body);
            boolean truncated = text.length() > maxLength;
            if (truncated) {
                text = text.substring(0, maxLength) + "\n...[truncated]";
            }

            return AiToolResult.ok(new FetchUrlResult(url, response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse("unknown"), title, text,
                    truncated));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return AiToolResult.failed("Fetch interrupted");
        } catch (IOException e) {
            return AiToolResult.failed("Failed to fetch URL: " + e.getMessage());
        }
    }

    private static String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        return matcher.find() ? cleanText(matcher.group(1)) : "";
    }

    private static String htmlToText(String html) {
        String withoutScripts = SCRIPT_PATTERN.matcher(html).replaceAll(" ");
        String withoutStyles = STYLE_PATTERN.matcher(withoutScripts).replaceAll(" ");
        String withoutTags = TAG_PATTERN.matcher(withoutStyles).replaceAll(" ");
        return cleanText(withoutTags);
    }

    private static String cleanText(String text) {
        String decoded = text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ");
        return WHITESPACE_PATTERN.matcher(decoded).replaceAll(" ").trim();
    }

    private static @Nullable Integer parsePositiveInt(String value) {
        if (value.isBlank()) {
            return FetchUrlTool.DEFAULT_MAX_LENGTH;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
