package org.togetherjava.tjbot.logwatcher.users;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.togetherjava.tjbot.logwatcher.accesscontrol.Role;
import org.togetherjava.tjbot.logwatcher.config.Config;
import org.togetherjava.tjbot.db.generated.tables.pojos.Users;

import java.util.List;
import java.util.Set;

/**
 * Service to load a spring UserDetail-Object from the userRepository, currently only used for
 * db-initialisation
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository, Config config) {
        this.userRepository = userRepository;

        if (this.userRepository.count() == 0) {
            final Users defaultUser =
                    new Users(Long.parseLong(config.getRootDiscordID()), config.getRootUserName());

            this.userRepository.save(defaultUser);
            this.userRepository.saveRolesForUser(defaultUser, Set.of(Role.ADMIN, Role.USER));
        }
    }

    private List<GrantedAuthority> getAuthorities(Users user) {
        return this.userRepository.fetchRolesForUser(user)
            .stream()
            .map(Role::getRoleName)
            .map(name -> "ROLE_" + name)
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();

    }

    /**
     * Loads the user from the userRepository and maps it to the Spring-Object UserDetails
     *
     * @param username Username of the User to Load
     * @return The UserDetail-Object that is associated with the discordID, or else throws an
     *         {@link UsernameNotFoundException}
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("No user present with username: " + username);
        } else {
            return new org.springframework.security.core.userdetails.User(user.getUsername(), null,
                    getAuthorities(user));
        }
    }
}
