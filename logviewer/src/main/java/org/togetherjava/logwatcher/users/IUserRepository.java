package org.togetherjava.logwatcher.users;

import org.togetherjava.logwatcher.accesscontrol.Role;
import org.togetherjava.tjbot.db.generated.tables.pojos.Users;

import java.util.List;
import java.util.Set;

/**
 * Basic JPA-Repository for loading Users from the DB
 */
public interface IUserRepository {

    /**
     * Load's the User from the DB, that matches the discordID
     *
     * @param discordID Discord-ID of the User
     * @return User-Object of the user where the discordID matches, else null
     */
    Users findByDiscordID(long discordID);

    /**
     * Load's the User from the DB, that matches the username
     *
     * @param username Username of the User to load
     * @return User-Object of the user where the username matches, else null
     */
    Users findByUsername(final String username);

    /**
     * Fetches all saved User
     *
     * @return List of Users from the DB, never null
     */
    List<Users> findAll();

    /**
     * Counts the amount of Users saved in the DB
     *
     * @return Count of Users in the db >=0
     */
    int count();

    /**
     * Merges the given user in the DB
     *
     * @param user User to Save in the DB
     */
    void save(Users user);

    /**
     * Removes this User and all referencing Entities
     */
    void delete(Users user);

    /**
     * Fetches the Roles the User has, see {@link Role}
     *
     * @param user User to fetch the Roles for
     * @return Set of Roles, never null
     */
    Set<Role> fetchRolesForUser(Users user);

    /**
     * Updates/Saves the Roles for the User
     *
     * @param user User to update the Role's
     * @param roles All Roles the User should have
     */
    void saveRolesForUser(Users user, Set<Role> roles);
}
