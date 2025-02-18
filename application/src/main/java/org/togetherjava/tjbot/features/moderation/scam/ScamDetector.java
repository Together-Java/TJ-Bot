package org.togetherjava.tjbot.features.moderation.scam;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detects whether a text message classifies as scam or not, using certain heuristics.
 *
 * Highly configurable, using {@link ScamBlockerConfig}. Main method to use is
 * {@link #isScam(CharSequence)}.
 */
public final class ScamDetector {
    private static final Pattern TOKENIZER = Pattern.compile("[\\s,]");
    private final ScamBlockerConfig config;

    /**
     * Creates a new instance with the given configuration
     * 
     * @param config the scam blocker config to use
     */
    public ScamDetector(Config config) {
        this.config = config.getScamBlocker();
    }

    /**
     * Detects whether the given message classifies as scam or not, using certain heuristics.
     * 
     * @param message the message to analyze
     * @return Whether the message classifies as scam
     */
    public boolean isScam(CharSequence message) {
        AnalyseResults results = new AnalyseResults();
        TOKENIZER.splitAsStream(message).forEach(token -> analyzeToken(token, results));
        return isScam(results);
    }

    private boolean isScam(AnalyseResults results) {
        if (results.pingsEveryone && (results.containsSuspiciousKeyword || results.hasUrl
                || results.containsDollarSign)) {
            return true;
        }

        return Stream
            .of(results.containsSuspiciousKeyword, results.hasSuspiciousUrl,
                    results.containsDollarSign)
            .filter(flag -> flag)
            .count() >= 2;
    }

    private void analyzeToken(String token, AnalyseResults results) {
        if (token.isBlank()) {
            return;
        }

        if (!results.pingsEveryone
                && ("@everyone".equalsIgnoreCase(token) || "@here".equalsIgnoreCase(token))) {
            results.pingsEveryone = true;
        }

        if (!results.containsSuspiciousKeyword && containsSuspiciousKeyword(token)) {
            results.containsSuspiciousKeyword = true;
        }

        if (!results.containsDollarSign && token.contains("$")) {
            results.containsDollarSign = true;
        }

        if (token.startsWith("http")) {
            analyzeUrl(token, results);
        }
    }

    private void analyzeUrl(String url, AnalyseResults results) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            // Invalid urls are not scam
            return;
        }

        if (host == null) {
            return;
        }

        results.hasUrl = true;

        if (config.getHostWhitelist().contains(host)) {
            return;
        }

        if (config.getHostBlacklist().contains(host)) {
            results.hasSuspiciousUrl = true;
            return;
        }

        for (String keyword : config.getSuspiciousHostKeywords()) {
            if (isHostSimilarToKeyword(host, keyword)) {
                results.hasSuspiciousUrl = true;
                break;
            }
        }
    }

    private boolean containsSuspiciousKeyword(String token) {
        String preparedToken = token.toLowerCase(Locale.US);

        return config.getSuspiciousKeywords()
            .stream()
            .map(keyword -> keyword.toLowerCase(Locale.US))
            .anyMatch(preparedToken::contains);
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

    private static class AnalyseResults {
        private boolean pingsEveryone;
        private boolean containsSuspiciousKeyword;
        private boolean containsDollarSign;
        private boolean hasUrl;
        private boolean hasSuspiciousUrl;
    }
}
