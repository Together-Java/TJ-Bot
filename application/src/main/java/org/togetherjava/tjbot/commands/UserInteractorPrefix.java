package org.togetherjava.tjbot.commands;

import org.jetbrains.annotations.Contract;

/**
 * Contains the prefixes for the UserInteractor's.
 * <p>
 * This is used for separate interactors with the same name, by command type (and possibly more in
 * the future). Our system doesn't allow multiple interactors with the same name, while having a
 * slash-command, and a message-context-command with the same name can be useful.
 */
public enum UserInteractorPrefix {
    // Implementations that are none of the following have no dedicated prefix.

    /**
     * Prefix for slash commands.
     */
    SLASH_COMMAND(SlashCommand.class, "s-"),
    /**
     * Prefix for message context commands.
     */
    MESSAGE_CONTEXT_COMMAND(MessageContextCommand.class, "mc-"),
    /**
     * Prefix for user context commands.
     */
    USER_CONTEXT_COMMAND(UserContextCommand.class, "uc-");

    private final Class<? extends UserInteractor> classType;
    private final String prefix;

    UserInteractorPrefix(Class<? extends UserInteractor> classType, final String prefix) {
        this.classType = classType;
        this.prefix = prefix;
    }

    /**
     * The prefix for the command
     *
     * @return the command's prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns the name, attached with the prefix in front of it.
     *
     * @param name the name
     * @return the name, with the prefix in front of it.
     */
    public String getPrefixedName(String name) {
        return prefix + name;
    }

    /**
     * The class type that should receive the prefix
     *
     * @return a {@link Class} instance of the type
     */
    public Class<? extends UserInteractor> getClassType() {
        return classType;
    }

    /**
     * Checks what enum value the given instance collaborates to.
     * <p>
     * This returns the name of the interactor, and adds the designated prefix to the name. As
     * example, a slash-command with the name "help" becomes "s-help".
     *
     * @param instance an instance to type check for a prefix
     * @param <T> the type of the instance
     * @return the interactor's name, with its prefix
     */
    public static <T extends UserInteractor> String getPrefixedNameFromInstance(final T instance) {
        String name = instance.getName();

        for (UserInteractorPrefix value : values()) {
            Class<? extends UserInteractor> valueClassType = value.getClassType();

            if (valueClassType.isInstance(instance)) {
                return value.getPrefix() + name;
            }
        }

        return name;
    }

    /**
     * Checks what enum value the given instance collaborates to.
     * <p>
     * This combines the given name, with the interactor's prefix. As example, a slash-command with
     * the name "help" becomes "s-help".
     *
     * @param clazz the class to get the prefix from
     * @param name the name of the instance
     * @param <T> the type of the instance
     * @return the prefixed {@link String}
     */
    public static <T extends UserInteractor> String getPrefixedNameFromClass(final Class<T> clazz,
            final String name) {

        for (UserInteractorPrefix value : values()) {
            Class<? extends UserInteractor> valueClassType = value.getClassType();

            if (valueClassType.isAssignableFrom(clazz)) {
                return value.getPrefix() + name;
            }
        }

        return name;
    }

    @Override
    @Contract(pure = true)
    public String toString() {
        return "UserInteractorPrefix{" + "prefix='" + prefix + '\'' + '}';
    }
}
