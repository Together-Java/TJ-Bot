package org.togetherjava.tjbot.features.basic;

/**
 * Represents a thread title, enforcing a maximum length of 60 characters. If an initial title
 * exceeds this limit, it's truncated at the last word boundary before or at the 60-character mark
 * to prevent cutting words mid-sentence. If no space is found, it truncates at 60 characters.
 * Provides a static factory method `withFallback` to create a ThreadTitle, using a fallback title
 * if the primary title is empty.
 */
public record ThreadTitle(String value) {

    private static final int TITLE_MAX_LENGTH = 60;

    public ThreadTitle(String value) {
        String threadTitle;
        if (value.length() >= TITLE_MAX_LENGTH) {
            int lastWordEnd = value.lastIndexOf(' ', TITLE_MAX_LENGTH);

            if (lastWordEnd == -1) {
                lastWordEnd = TITLE_MAX_LENGTH;
            }

            threadTitle = value.substring(0, lastWordEnd);
        } else {
            threadTitle = value;
        }

        this.value = threadTitle;
    }

    public static ThreadTitle withFallback(String primary, String fallback) {
        if (!primary.isEmpty()) {
            return new ThreadTitle(primary);
        } else {
            return new ThreadTitle(fallback);
        }
    }

    @Override
    public String toString() {
        return value;
    }

}
