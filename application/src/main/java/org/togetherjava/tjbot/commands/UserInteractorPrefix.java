package org.togetherjava.tjbot.commands;

import org.jetbrains.annotations.Contract;

/**
 * Contains the prefixes for the UserInteractor's.
 * <p>
 * This is used for separate interactors with the same name, by command type (and possibly more in
 * the future). Our system doesn't allow multiple interactors with the same name, while having a
 * slash-command and a message-context-command with the same name can be really useful.
 */
public enum UserInteractorPrefix {
    SLASH_COMMAND("s-"),
    MESSAGE_CONTEXT_COMMAND("mc-"),
    USER_CONTEXT_COMMAND("uc-");

    private final String prefix;

    UserInteractorPrefix(final String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Checks what enum value the given instance collaborates to.
     * <p>
     * As example,
     *
     * @param instance an instance to type check for a prefix
     * @param <T> the type of the instance
     * @return the prefixed {@link String}
     */
    public static <T extends UserInteractor> String getPrefixFromInstance(final T instance) {
        String name = instance.getName();

        String prefix = "";

        // If this gets bigger, add a class variable to the constructor, but for now keep it simple
        // / hardcoded, if we rework this, we should also add a check that makes sure there's no
        // prefixes in names
        if (instance instanceof SlashCommand) {
            prefix = SLASH_COMMAND.getPrefix();
        } else if (instance instanceof MessageContextCommand) {
            prefix = MESSAGE_CONTEXT_COMMAND.getPrefix();
        } else if (instance instanceof UserContextCommand) {
            prefix = USER_CONTEXT_COMMAND.getPrefix();
        }

        return prefix + name;
    }

    @Override
    @Contract(pure = true)
    public String toString() {
        return "UserInteractorPrefix{" + "prefix='" + prefix + '\'' + '}';
    }
}
