package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.ScamBlockerConfig;

import java.util.Collection;
import java.util.List;
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
    private final Predicate<String> hasTrustedRole;
    private final TokenAnalyse tokenAnalyse;

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

        tokenAnalyse = new TokenAnalyse(this.config);
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
        TOKENIZER.splitAsStream(message).forEach(token -> tokenAnalyse.analyze(token, results));
        return isScam(results);
    }

    private boolean isScam(AnalyseResults results) {
        if (results.pingsEveryone() && (results.containsSuspiciousKeyword() || results.hasUrl()
                || results.containsDollarSign())) {
            return true;
        }

        boolean hasTooManySuspiciousFlags = Stream
            .of(results.containsSuspiciousKeyword(), results.hasSuspiciousUrl(),
                    results.containsDollarSign())
            .filter(flag -> flag)
            .count() >= 2;
        if (hasTooManySuspiciousFlags) {
            return true;
        }

        return results.onlyContainsUrls() && results.areAllUrlsWithAttachments()
                && areAttachmentsSuspicious(results.getUrlAttachments());
    }

    private boolean areAttachmentsSuspicious(Collection<Attachment> attachments) {
        long suspiciousAttachments =
                attachments.stream().filter(this::isAttachmentSuspicious).count();
        return suspiciousAttachments >= config.getSuspiciousAttachmentsThreshold();
    }

    private boolean isAttachmentSuspicious(Attachment attachment) {
        return attachment.isImage() && isSuspiciousAttachmentName.test(attachment.fileName());
    }
}
