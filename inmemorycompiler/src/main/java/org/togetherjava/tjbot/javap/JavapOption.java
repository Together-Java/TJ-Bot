package org.togetherjava.tjbot.javap;


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

    private final String option;

    JavapOption(String option) {
        this.option = option;
    }

    /**
     * @return textual representation of the option
     */
    public String getOption() {
        return option;
    }
}
