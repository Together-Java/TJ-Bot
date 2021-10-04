package org.togetherjava.logwatcher.accesscontrol;

import java.util.Arrays;
import java.util.Set;

/**
 * Basic Roles for Access Control on Views
 */
public enum Role {
    /**
     * Role for when Stuff goes wrong
     */
    UNKNOWN(0, "unknown"),

    /**
     * Base Role
     */
    USER(1, "user"),

    /**
     * Role for Views that should require more permissions
     */
    ADMIN(2, "admin");

    private final int id;
    private final String roleName;

    Role(int id, String roleName) {
        this.id = id;
        this.roleName = roleName;
    }

    public int getId() {
        return id;
    }

    public String getRoleName() {
        return roleName;
    }

    public static Role forID(final int id) {
        return Arrays.stream(values())
            .filter(r -> r.id == id)
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException("Unknown RoleID: %d".formatted(id)));
    }

    public static Set<Role> getDisplayableRoles() {
        return Set.of(USER, ADMIN);
    }

}
