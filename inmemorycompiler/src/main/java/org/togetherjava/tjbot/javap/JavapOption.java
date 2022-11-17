package org.togetherjava.tjbot.javap;

/**
 * Selectable options for {@link Javap}. See the official
 * <a href="https://docs.oracle.com/en/java/javase/18/docs/specs/man/javap.html">documentation</a>
 * for details.
 */
public enum JavapOption {
    /**
     * Shows additional information about the selected class.
     */
    VERBOSE("-verbose"),
    /**
     * Shows line and local variable tables.
     */
    LINE("-l"),
    /**
     * Shows only public classes and members.
     */
    PUBLIC("-public"),
    /**
     * Shows only protected and public classes and members.
     */
    PROTECTED("-protected"),
    /**
     * Shows package/protected/public classes and members (default).
     */
    PACKAGE("-package"),
    /**
     * Shows all classes and members.
     */
    PRIVATE("-private"),
    /**
     * Shows disassembled code, for example, the instructions that comprise the Java bytecodes, for
     * each of the methods in the class.
     */
    DISASSEMBLE_CODE("-c"),
    /**
     * Shows internal type signatures.
     */
    INTERNAL_TYPE_SIGNATURES("-s"),
    /**
     * Shows system information (path, size, date, SHA-256 hash) of the class being processed.
     */
    SYSTEM_INFO("-sysinfo"),
    /**
     * Shows static final constants.
     */
    CONSTANTS("-constants");

    private final String label;

    JavapOption(String label) {
        this.label = label;
    }

    /**
     * Gets the label of this option.
     *
     * @return textual representation of the option
     */
    public String getLabel() {
        return label;
    }
}
