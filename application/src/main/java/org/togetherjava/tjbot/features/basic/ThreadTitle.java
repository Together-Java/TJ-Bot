
package org.togetherjava.tjbot.features.basic;

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

    public static ThreadTitle withFallback(String threadtitle, String fallback) {
        if (!threadtitle.isEmpty()) {
            return new ThreadTitle(threadtitle);
        } else {
            return new ThreadTitle(fallback);
        }
    }

    @Override
    public String toString() {
        return value;
    }

}
