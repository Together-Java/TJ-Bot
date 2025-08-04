package org.togetherjava.tjbot.features.moderation.scam;

import org.togetherjava.tjbot.config.ScamBlockerConfig;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.net.URI;
import java.util.Locale;

/**
 * Analyzes a given text token. Populates various metrics regarding the token possibly being
 * suspicious, returning back results of the token analyze.
 *
 * Highly configurable, using {@link ScamBlockerConfig}. Entry point to use is
 * {@link #analyze(String, AnalyseResults)}.
 */
final class TokenAnalyse {
    private final ScamBlockerConfig config;

    TokenAnalyse(ScamBlockerConfig config) {
        this.config = config;
    }

    /**
     * Analyzes the given token about being suspicious.
     * 
     * @param token the token to analyze
     * @param results metrics representing how suspicious the token is
     */
    void analyze(String token, AnalyseResults results) {
        if (token.isBlank()) {
            return;
        }

        if (!results.pingsEveryone()
                && ("@everyone".equalsIgnoreCase(token) || "@here".equalsIgnoreCase(token))) {
            results.markPingsEveryone();
        }

        if (!results.containsSuspiciousKeyword() && containsSuspiciousKeyword(token)) {
            results.markContainsSuspiciousKeyword();
        }

        if (!results.containsDollarSign()
                && (token.contains("$") || "usd".equalsIgnoreCase(token))) {
            results.markContainsDollarSign();
        }

        if (token.startsWith("http")) {
            analyzeUrl(token, results);
        } else {
            results.markNonUrlTokenFound();
        }
    }

    private boolean containsSuspiciousKeyword(String token) {
        String preparedToken = token.toLowerCase(Locale.US);

        // Checks the token against various keywords from the config
        // The keywords support some regex-inspired syntax
        return config.getSuspiciousKeywords()
            .stream()
            .map(keyword -> keyword.toLowerCase(Locale.US))
            .anyMatch(keyword -> {
                // Exact match "^foo$"
                if (startsWith(keyword, '^') && endsWith(keyword, '$')) {
                    return preparedToken.equals(keyword.substring(1, keyword.length() - 1));
                }
                // Simple regex-inspired syntax "^foo"
                if (startsWith(keyword, '^')) {
                    return preparedToken.startsWith(keyword.substring(1));
                }
                // Simple regex-inspired syntax "foo$"
                if (endsWith(keyword, '$')) {
                    return preparedToken.endsWith(keyword.substring(0, keyword.length() - 1));
                }
                return preparedToken.contains(keyword);
            });
    }

    private void analyzeUrl(String url, AnalyseResults results) {
        String host;
        String path;
        try {
            URI uri = URI.create(url);
            host = uri.getHost();
            path = uri.getPath();
        } catch (IllegalArgumentException _) {
            // Invalid urls are not scam
            return;
        }

        if (host == null) {
            return;
        }

        AnalyseResults.AnalyseUrlResult result = new AnalyseResults.AnalyseUrlResult();
        results.addUrlResult(result);

        if (path != null && path.startsWith("/attachments")) {
            // The url represents an attachment link, for example a Discord CDN link
            result.setContainedAttachment(Attachment.fromUrlPath(path));
        }

        if (isHostSuspicious(host)) {
            result.markSuspicious();
        }
    }

    private boolean isHostSuspicious(String host) {
        if (config.getHostWhitelist().contains(host)) {
            return false;
        }

        if (config.getHostBlacklist().contains(host)) {
            return true;
        }

        for (String keyword : config.getSuspiciousHostKeywords()) {
            if (isHostSimilarToKeyword(host, keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean isHostSimilarToKeyword(String host, String keyword) {
        // NOTE This algorithm is far from optimal.
        // It is good enough for our purpose though and not that complex.

        // Rolling window of keyword-size over host.
        // If any window has a small distance, it is similar
        int windowStart = 0;
        int windowEnd = keyword.length();
        while (windowEnd <= host.length()) {
            String window = host.substring(windowStart, windowEnd);
            int distance = StringDistances.editDistance(keyword, window);

            if (distance <= config.getIsHostSimilarToKeywordDistanceThreshold()) {
                return true;
            }

            windowStart++;
            windowEnd++;
        }

        return false;
    }

    private static boolean startsWith(CharSequence text, char prefixToTest) {
        return !text.isEmpty() && text.charAt(0) == prefixToTest;
    }

    private static boolean endsWith(CharSequence text, char suffixToTest) {
        return !text.isEmpty() && text.charAt(text.length() - 1) == suffixToTest;
    }
}
