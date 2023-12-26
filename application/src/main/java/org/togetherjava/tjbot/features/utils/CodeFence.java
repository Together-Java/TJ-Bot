package org.togetherjava.tjbot.features.utils;

import javax.annotation.Nullable;

/**
 * Represents code in a code-fence.
 *
 * @param language the language of the code, optional
 * @param code the code itself
 */
public record CodeFence(@Nullable String language, String code) {
    /**
     * Creates the Discord- and markdown-conform representation of this code-fence.
     *
     * @return this code fence in markdown
     */
    public String toMarkdown() {
        return """
                ```%s
                %s
                ```
                """.formatted(language == null ? "" : language, code);
    }
}
