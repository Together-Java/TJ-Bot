package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;

/**
 * Enum that represents every possible javac executable option that does not include an argument.
 */
public enum JavacOption {
    DEBUG_ALL("-g"),
    DEBUG_NONE("-g:none"),
    NOWARN("-nowarn"),
    VERBOSE("-verbose"),
    DEPRECATION("-deprecation"),
    PARAMETERS("-parameters"),
    WERROR("-Werror");

    private final @NotNull String option;

    JavacOption(@NotNull String option) {
        this.option = option;
    }

    /**
     * @return textual representation of the option
     */
    public @NotNull String getOption() {
        return option;
    }
}
