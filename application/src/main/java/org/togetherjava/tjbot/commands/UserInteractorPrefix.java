package org.togetherjava.tjbot.commands;

import org.jetbrains.annotations.Contract;

/**
 * Contains the prefixes for the UserInteractor's.
 * <p>
 * This is used for separate interactors with the same name, by command type (and possibly more in
 * the future). Our system doesn't allow multiple interactors with the same name, while having a
 * slash-command and a message-context-command with the same name can be really useful.
 * <p>
 * The
 */
public enum UserInteractorPrefix {
    /*
     * order CANNOT be modified This is because USER_INTERACTOR applies to every interactor, but
     * still I thought it'd be useful to document there's no prefix.
     */

    SLASH_COMMAND(SlashCommand.class, "s-"),
    MESSAGE_CONTEXT_COMMAND(MessageContextCommand.class, "mc-"),
    USER_CONTEXT_COMMAND(UserContextCommand.class, "uc-"),
    USER_INTERACTOR(UserInteractor.class, "");


    private final Class<? extends UserInteractor> classType;
    private final String prefix;

    UserInteractorPrefix(Class<? extends UserInteractor> classType, final String prefix) {
        this.classType = classType;
        this.prefix = prefix;
    }

    /**
     * The prefix for the command
     *
     * @return
     */
    public String getPrefix() {
        return prefix;
    }


    /**
     * The class type that should receive the prefix
     *
     * @return
     */
    public Class<? extends UserInteractor> getClassType() {
        return classType;
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
     * As example,
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
