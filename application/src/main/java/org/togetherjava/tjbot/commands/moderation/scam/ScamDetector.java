package org.togetherjava.tjbot.commands.moderation.scam;

import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.utils.StringDistances;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;

import java.net.URI;
import java.util.regex.Pattern;

public class ScamDetector {
    private static final Pattern TOKENIZER = Pattern.compile("[\\s,]");
    private final ScamBlockerConfig config;

    public ScamDetector(@NotNull Config config) {
        this.config = config.getScamBlocker();
    }

    public boolean isScam(@NotNull CharSequence message) {
        AnalyseResults results = new AnalyseResults();
        TOKENIZER.splitAsStream(message).forEach(token -> analyzeToken(token, results));
        return isScam(results);
    }

    private boolean isScam(@NotNull AnalyseResults results) {
        if (results.pingsEveryone && results.containsNitroKeyword && results.hasUrl) {
            return true;
        }
        return results.containsNitroKeyword && results.hasSuspiciousUrl;
    }

    private void analyzeToken(@NotNull String token, @NotNull AnalyseResults results) {
        if ("@everyone".equalsIgnoreCase(token)) {
            results.pingsEveryone = true;
        }
        if ("nitro".equalsIgnoreCase(token)) {
            results.containsNitroKeyword = true;
        }

        if (token.startsWith("http")) {
            analyzeUrl(token, results);
        }
    }

    private void analyzeUrl(@NotNull String url, @NotNull AnalyseResults results) {
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

    private boolean isHostSimilarToKeyword(@NotNull String host, @NotNull String keyword) {
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
        private boolean containsNitroKeyword;
        private boolean hasUrl;
        private boolean hasSuspiciousUrl;
    }
}
