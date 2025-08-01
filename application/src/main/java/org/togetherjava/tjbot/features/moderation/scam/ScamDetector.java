package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;
import org.togetherjava.tjbot.features.utils.StringDistances;

import javax.annotation.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "tiff", "svg", "apng");
    private static final Pattern TOKENIZER = Pattern.compile("[\\s,]");
    private final ScamBlockerConfig config;
    private final Predicate<String> isSuspiciousAttachmentName;
    private final Predicate<String> hasTrustedRole;

    /**
     * Creates a new instance with the given configuration
     * 
     * @param config the scam blocker config to use
     */
    public ScamDetector(Config config) {
        this.config = config.getScamBlocker();

        isSuspiciousAttachmentName =
                Pattern.compile(this.config.getSuspiciousAttachmentNamePattern())
                    .asMatchPredicate();
        hasTrustedRole =
                Pattern.compile(this.config.getTrustedUserRolePattern()).asMatchPredicate();
    }

    /**
     * Detects whether the given message classifies as scam or not, using certain heuristics.
     *
     * @param message the message to analyze
     * @return Whether the message classifies as scam
     */
    public boolean isScam(Message message) {
        Member author = message.getMember();
        boolean isTrustedUser = author != null
                && author.getRoles().stream().map(Role::getName).anyMatch(hasTrustedRole);
        if (isTrustedUser) {
            return false;
        }

        String content = message.getContentDisplay();
        List<Attachment> attachments =
                message.getAttachments().stream().map(Attachment::fromDiscord).toList();

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
        results.onlyContainsUrls = true;
        TOKENIZER.splitAsStream(message).forEach(token -> analyzeToken(token, results));
        return isScam(results);
    }

    private boolean isScam(AnalyseResults results) {
        if (results.pingsEveryone && (results.containsSuspiciousKeyword || results.hasUrl()
                || results.containsDollarSign)) {
            return true;
        }

        boolean hasTooManySuspiciousFlags = Stream
            .of(results.containsSuspiciousKeyword, results.hasSuspiciousUrl(),
                    results.containsDollarSign)
            .filter(flag -> flag)
            .count() >= 2;
        if (hasTooManySuspiciousFlags) {
            return true;
        }

        return results.onlyContainsUrls && results.areAllUrlsWithAttachments()
                && areAttachmentsSuspicious(results.getUrlAttachments());
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
        } else {
            results.onlyContainsUrls = false;
        }
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

        AnalyseUrlResult result = new AnalyseUrlResult();
        results.urls.add(result);

        if (path != null && path.startsWith("/attachments")) {
            result.containedAttachment = Attachment.fromUrlPath(path);
        }

        if (config.getHostWhitelist().contains(host)) {
            return;
        }

        if (config.getHostBlacklist().contains(host)) {
            result.isSuspicious = true;
            return;
        }

        for (String keyword : config.getSuspiciousHostKeywords()) {
            if (isHostSimilarToKeyword(host, keyword)) {
                result.isSuspicious = true;
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

    private boolean areAttachmentsSuspicious(Collection<Attachment> attachments) {
        long suspiciousAttachments =
                attachments.stream().filter(this::isAttachmentSuspicious).count();
        return suspiciousAttachments >= config.getSuspiciousAttachmentsThreshold();
    }

    private boolean isAttachmentSuspicious(Attachment attachment) {
        return attachment.isImage() && isSuspiciousAttachmentName.test(attachment.fileName());
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

    private record Attachment(String fileName) {
        boolean isImage() {
            return getFileExtension().map(IMAGE_EXTENSIONS::contains).orElse(false);
        }

        private Optional<String> getFileExtension() {
            int dot = fileName.lastIndexOf('.');
            if (dot == -1) {
                return Optional.empty();
            }
            String extension = fileName.substring(dot + 1);
            return Optional.of(extension);
        }

        static Attachment fromDiscord(Message.Attachment attachment) {
            return new Attachment(attachment.getFileName());
        }

        static Attachment fromUrlPath(String urlPath) {
            int fileNameStart = urlPath.lastIndexOf('/');
            String fileName = fileNameStart == -1 ? "" : urlPath.substring(fileNameStart + 1);
            return new Attachment(fileName);
        }
    }

    private static final class AnalyseUrlResult {
        private boolean isSuspicious;
        @Nullable
        private Attachment containedAttachment;

        @Override
        public String toString() {
            return new StringJoiner(", ", AnalyseUrlResult.class.getSimpleName() + "[", "]")
                .add("isSuspicious=" + isSuspicious)
                .add("containedAttachment=" + containedAttachment)
                .toString();
        }
    }

    private static final class AnalyseResults {
        private boolean pingsEveryone;
        private boolean containsSuspiciousKeyword;
        private boolean containsDollarSign;
        private boolean onlyContainsUrls;
        private final Collection<AnalyseUrlResult> urls = new ArrayList<>();

        boolean hasUrl() {
            return !urls.isEmpty();
        }

        boolean hasSuspiciousUrl() {
            return urls.stream().anyMatch(url -> url.isSuspicious);
        }

        boolean areAllUrlsWithAttachments() {
            return urls.stream().allMatch(url -> url.containedAttachment != null);
        }

        Collection<Attachment> getUrlAttachments() {
            return urls.stream()
                .map(url -> url.containedAttachment)
                .filter(Objects::nonNull)
                .toList();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", AnalyseResults.class.getSimpleName() + "[", "]")
                .add("pingsEveryone=" + pingsEveryone)
                .add("containsSuspiciousKeyword=" + containsSuspiciousKeyword)
                .add("containsDollarSign=" + containsDollarSign)
                .add("onlyContainsUrls=" + onlyContainsUrls)
                .add("urls=" + urls)
                .toString();
        }
    }
}
