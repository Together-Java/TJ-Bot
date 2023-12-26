package org.togetherjava.tjbot.features.moderation;

/**
 * All available moderation actions.
 */
public enum ModerationAction {
    /**
     * When a user bans another user.
     */
    BAN("banned"),
    /**
     * When a user unbans another user.
     */
    UNBAN("unbanned"),
    /**
     * When a user kicks another user.
     */
    KICK("kicked"),
    /**
     * When a user warns another user.
     */
    WARN("warned"),
    /**
     * When a user mutes another user.
     */
    MUTE("muted"),
    /**
     * When a user unmutes another user.
     */
    UNMUTE("unmuted"),
    /**
     * When a user quarantines another user.
     */
    QUARANTINE("quarantined"),
    /**
     * When a user unquarantines another user.
     */
    UNQUARANTINE("unquarantined"),
    /**
     * When a user writes a note about another user.
     */
    NOTE("wrote a note about");

    private final String verb;

    /**
     * Creates an instance with the given verb
     *
     * @param verb the verb of the action, as it would be used in a sentence, such as "banned" or
     *        "kicked"
     */
    ModerationAction(String verb) {
        this.verb = verb;
    }

    /**
     * Gets the verb of the action, as it would be used in a sentence.
     * <p>
     * Such as "banned" or "kicked"
     *
     * @return the verb of this action
     */
    public String getVerb() {
        return verb;
    }
}
