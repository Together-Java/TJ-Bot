package org.togetherjava.logwatcher.entities;

import com.vaadin.fusion.Nonnull;
import org.springframework.data.jpa.domain.AbstractPersistable;
import org.togetherjava.logwatcher.accesscontrol.Role;

import javax.persistence.*;
import java.util.Objects;
import java.util.Set;

/**
 * Basic User-Object containing the unique internal ID + DiscordID, the Name and the Roles the user
 * possesses
 */

@Entity
@Table(name = "APPUSERS",
        uniqueConstraints = @UniqueConstraint(name = "DISCORD_ID", columnNames = {"discordID"}))
public class User extends AbstractPersistable<Integer> {

    private String username;
    private String name;

    @Nonnull
    private long discordID;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Role> roles;

    public long getDiscordID() {
        return discordID;
    }

    public void setDiscordID(long discordID) {
        this.discordID = discordID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof User other))
            return false;

        return discordID == other.discordID && Objects.equals(username, other.username)
                && Objects.equals(name, other.name) && !Objects.equals(roles, other.roles);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (int) (discordID ^ (discordID >>> 32));
        result = 31 * result + (roles != null ? roles.hashCode() : 0);
        return result;
    }
}
