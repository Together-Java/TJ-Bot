package org.togetherjava.logwatcher.accesscontrol;

/**
 * Basic Roles for Access Control on Views
 */
public enum Role {
    /**
     * Base Role
     */
    USER("user"),

    /**
     * Role for Views that should require more permissions
     */
    ADMIN("admin");

    private final String roleName;

    Role(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleName() {
        return roleName;
    }

}
