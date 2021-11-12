package org.togetherjava.tjbot.javap;

import org.jetbrains.annotations.NotNull;

/**
 * Enum that represents every possible javap executable option that does not include an argument.
 */
public enum JavapOption {
    VERBOSE("-verbose"),
    LINE("-l"),
    PUBLIC("-public"),
    PROTECTED("-protected"),
    PACKAGE("-package"),
    PRIVATE("-private"),
    DISASSEMBLE_CODE("-c"),
    INTERNAL_TYPE_SIGNATURES("-s"),
    SYSTEM_INFO("-sysinfo"),
    CONSTANTS("-constants");

    private final @NotNull String option;

    JavapOption(@NotNull String option) {
        this.option = option;
    }

    /**
     * @return textual representation of the option
     */
    public @NotNull String getOption() {
        return option;
    }
}
