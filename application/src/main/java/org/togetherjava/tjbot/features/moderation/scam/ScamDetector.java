package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Message;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Predicate;
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
    private final Predicate<String> isSuspiciousAttachmentName;

    /**
     * Creates a new instance with the given configuration
     * 
     * @param config the scam blocker config to use
     */
    public ScamDetector(Config config) {
        this.config = config.getScamBlocker();
        isSuspiciousAttachmentName =
                Pattern.compile(config.getScamBlocker().getSuspiciousAttachmentNamePattern())
                    .asMatchPredicate();
    }

    /**
     * Detects whether the given message classifies as scam or not, using certain heuristics.
     *
     * @param message the message to analyze
     * @return Whether the message classifies as scam
     */
    public boolean isScam(Message message) {
        String content = message.getContentDisplay();
        List<Message.Attachment> attachments = message.getAttachments();

        if (content.isBlank()) {
            return areAttachmentsSuspicious(attachments);
        }

        return isScam(content);
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

        if (!results.containsDollarSign && (token.contains("$") || "usd".equalsIgnoreCase(token))) {
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
        } catch (IllegalArgumentException _) {
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

    private boolean areAttachmentsSuspicious(Collection<? extends Message.Attachment> attachments) {
        long suspiciousAttachments =
                attachments.stream().filter(this::isAttachmentSuspicious).count();
        return suspiciousAttachments >= config.getSuspiciousAttachmentsThreshold();
    }

    private boolean isAttachmentSuspicious(Message.Attachment attachment) {
        return attachment.isImage() && isSuspiciousAttachmentName.test(attachment.getFileName());
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

    private static class AnalyseResults {
        private boolean pingsEveryone;
        private boolean containsSuspiciousKeyword;
        private boolean containsDollarSign;
        private boolean hasUrl;
        private boolean hasSuspiciousUrl;

        @Override
        public String toString() {
            return new StringJoiner(", ", AnalyseResults.class.getSimpleName() + "[", "]")
                .add("pingsEveryone=" + pingsEveryone)
                .add("containsSuspiciousKeyword=" + containsSuspiciousKeyword)
                .add("containsDollarSign=" + containsDollarSign)
                .add("hasUrl=" + hasUrl)
                .add("hasSuspiciousUrl=" + hasSuspiciousUrl)
                .toString();
        }
    }
}
