package org.togetherjava.logwatcher.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.togetherjava.logwatcher.entities.User;

/**
 * Basic JPA-Repository for loading Users from the DB
 */
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Load's the User from the DB, that matches the discordID
     *
     * @param discordID Discord-ID of the User
     * @return User-Object of the user where the discordID matches, else null
     */
    User findByDiscordID(long discordID);

    /**
     * Load's the User from the DB, that matches the username
     *
     * @param username Username of the User to load
     * @return User-Object of the user where the username matches, else null
     */
    User findByUsername(final String username);
}
