package org.togetherjava.tjbot.features;

/**
 * Different types of user interactions.
 * <p>
 * Also allows user interactors of different type to have the same name.
 */
public enum UserInteractionType {
    /**
     * User types a command leaded by a slash, such as {@code /ping}.
     */
    SLASH_COMMAND(SlashCommand.class, "s-"),
    /**
     * User right-clicks a message and selects a command.
     */
    MESSAGE_CONTEXT_COMMAND(MessageContextCommand.class, "mc-"),
    /**
     * User right-clicks a user and selects a command.
     */
    USER_CONTEXT_COMMAND(UserContextCommand.class, "uc-"),
    /**
     * Other types of interactions, for example a routine that offers buttons to click on.
     */
    OTHER(UserInteractor.class, "");

    private final Class<? extends UserInteractor> classType;
    private final String prefix;

    UserInteractionType(Class<? extends UserInteractor> classType, final String prefix) {
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
}
