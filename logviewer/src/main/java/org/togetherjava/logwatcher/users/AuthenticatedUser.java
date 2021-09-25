package org.togetherjava.logwatcher.users;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.togetherjava.logwatcher.entities.User;

import java.util.Collections;
import java.util.Optional;

/**
 * Wrapper for accessing the current User
 */
@Component
public class AuthenticatedUser {

    private final UserRepository userRepository;

    public AuthenticatedUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Check's if you could register the current User
     *
     * @return true, if the Current user is Authenticated
     */
    public boolean canRegister() {
        return getAuthenticatedUser().isPresent();
    }

    /**
     * Check's if the current User is already registered
     *
     * @return true, if the User is already in the repository
     */
    public boolean isRegistered() {
        if (!canRegister()) {
            return false;
        }

        return getAuthenticatedUser().map(this::extractID)
            .map(userRepository::findByDiscordID)
            .isPresent();
    }

    /**
     * Attempts to register the current User, if he is not yet registered
     */
    public void register() {
        if (!canRegister() || isRegistered()) {
            throw new IllegalStateException("Can not register an already registered User");
        }

        getAuthenticatedUser().map(this::toUser).ifPresent(userRepository::save);
    }

    /**
     * Get's the current User Object from the repository
     *
     * @return The Optional User, should in most cases not be empty
     */
    public Optional<User> get() {
        return getAuthenticatedUser().map(this::extractID).map(userRepository::findByDiscordID);
    }

    /**
     * Performs a logout on the current User
     */
    public void logout() {
        UI.getCurrent().getPage().setLocation("/");
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.logout(VaadinServletRequest.getCurrent().getHttpServletRequest(), null, null);
    }

    /**
     * Extracts the principal from the current SessionContext of the user
     *
     * @return The Optional Principal of the current User
     */
    private Optional<OAuth2User> getAuthenticatedUser() {
        SecurityContext context = SecurityContextHolder.getContext();
        Object principal = context.getAuthentication().getPrincipal();

        return principal instanceof OAuth2User ? Optional.of(principal).map(OAuth2User.class::cast)
                : Optional.empty();
    }

    /**
     * Maps the principal to the User object
     *
     * @param oAuth2User Principal to map
     * @return User-Object derived from the Principal
     */
    private User toUser(OAuth2User oAuth2User) {
        final User user = new User();
        user.setUsername(oAuth2User.getName());
        user.setName(oAuth2User.getName());
        user.setRoles(Collections.emptySet());

        return user;
    }

    /**
     * Extracts the discord-ID from the Principal
     *
     * @param oAuth2User Principal with the ID
     * @return Discord-ID from the given Principal
     */
    private long extractID(OAuth2User oAuth2User) {
        return Long.parseLong(oAuth2User.getAttribute("id"));
    }

}
