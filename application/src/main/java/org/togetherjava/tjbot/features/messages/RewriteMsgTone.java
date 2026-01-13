package org.togetherjava.tjbot.features.messages;

/**
 * Enum representing the available tone/style options for message rewriting.
 * <p>
 * Each tone provides a specific instruction to ChatGPT for how to approach the rewrite.
 */
public enum RewriteMsgTone {
    CLEAR("Clear"),
    PRO("Pro"),
    DETAILED("Detailed"),
    TECHNICAL("Technical");

    private final String displayName;

    RewriteMsgTone(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name of this tone.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the prompt instruction for this tone.
     *
     * @return the prompt instruction to include in ChatGPT prompt
     */
    public String getPromptInstruction() {
        return switch (this) {
            case CLEAR -> "Make it clear and easy to understand.";
            case PRO -> "Use a professional and polished tone.";
            case DETAILED -> "Expand with more detail and explanation.";
            case TECHNICAL -> "Use technical and specialized language where appropriate.";
        };
    }
}
