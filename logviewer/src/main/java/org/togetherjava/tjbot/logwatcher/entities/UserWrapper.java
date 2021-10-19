package org.togetherjava.tjbot.logwatcher.entities;

import org.togetherjava.tjbot.logwatcher.accesscontrol.Role;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class UserWrapper implements Serializable {
    @Serial
    private static final long serialVersionUID = -3701246411434315431L;

    private long discordID;
    private String userName;
    private Set<Role> roles = Collections.emptySet();

    public UserWrapper() {}

    public UserWrapper(long discordID, String userName, Set<Role> roles) {
        this.discordID = discordID;
        this.userName = userName;
        this.roles = roles;
    }

    public void setDiscordID(long discordID) {
        this.discordID = discordID;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public long getDiscordID() {
        return discordID;
    }

    public String getUserName() {
        return userName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserWrapper other)) {
            return true;
        }
        return this.discordID == other.discordID && Objects.equals(this.userName, other.userName)
                && Objects.equals(this.roles, other.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(discordID, userName, roles);
    }

    @Override
    public String toString() {
        return "UserDTO[" + "discordID=" + discordID + ", " + "userName=" + userName + ", "
                + "roles=" + roles + ']';
    }


}
