package org.togetherjava.tjbot.imc;

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

    private final String option;

    JavacOption(String option) {
        this.option = option;
    }

    /**
     * @return textual representation of the option
     */
    public String getOption() {
        return option;
    }
}
