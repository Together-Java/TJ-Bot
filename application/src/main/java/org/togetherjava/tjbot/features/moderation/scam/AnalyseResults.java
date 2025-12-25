package org.togetherjava.tjbot.features.moderation.scam;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;

final class AnalyseResults {
    private boolean pingsEveryone;
    private boolean containsSuspiciousKeyword;
    private boolean containsDollarSign;
    private boolean onlyContainsUrls = true;
    private final Collection<AnalyseUrlResult> urls = new ArrayList<>();

    void addUrlResult(AnalyseUrlResult result) {
        urls.add(result);
    }

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
        return urls.stream().map(url -> url.containedAttachment).filter(Objects::nonNull).toList();
    }

    boolean pingsEveryone() {
        return pingsEveryone;
    }

    void markPingsEveryone() {
        pingsEveryone = true;
    }

    boolean containsSuspiciousKeyword() {
        return containsSuspiciousKeyword;
    }

    void markContainsSuspiciousKeyword() {
        containsSuspiciousKeyword = true;
    }

    boolean containsDollarSign() {
        return containsDollarSign;
    }

    void markContainsDollarSign() {
        containsDollarSign = true;
    }

    boolean onlyContainsUrls() {
        return onlyContainsUrls;
    }

    void markNonUrlTokenFound() {
        onlyContainsUrls = false;
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

    static final class AnalyseUrlResult {
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

        boolean isSuspicious() {
            return isSuspicious;
        }

        void markSuspicious() {
            isSuspicious = true;
        }

        void setContainedAttachment(Attachment containedAttachment) {
            this.containedAttachment = containedAttachment;
        }
    }
}
