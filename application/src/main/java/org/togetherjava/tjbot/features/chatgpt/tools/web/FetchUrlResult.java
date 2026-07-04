package org.togetherjava.tjbot.features.chatgpt.tools.web;

/**
 * Payload returned by the {@code fetch_url} tool when it successfully retrieves a page. Serialized
 * to JSON and handed back to the model so it can quote, summarize, or reason over the page text.
 *
 * @param url the final URL after any redirects
 * @param statusCode HTTP status code returned by the server
 * @param contentType {@code Content-Type} header reported by the server, or a sensible default if
 *        the server omitted it
 * @param title best-effort document title (e.g. the HTML {@code <title>} element); empty string
 *        when none could be extracted
 * @param text extracted plain-text content suitable for feeding to the model
 * @param truncated {@code true} if {@link #text()} was cut short to fit within the per-tool size
 *        budget
 */
public record FetchUrlResult(String url, int statusCode, String contentType, String title,
        String text, boolean truncated) {
}
