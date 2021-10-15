package org.togetherjava.tjbot.logwatcher.oauth;

import com.vaadin.flow.spring.security.VaadinWebSecurityConfigurerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.togetherjava.tjbot.logwatcher.config.Config;

/**
 * Configures Spring Security so that we use Discord-OAuth2 as the identity provider
 */
@Configuration
public class OAuth2LoginConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(Config config) {
        return new InMemoryClientRegistrationRepository(googleClientRegistration(config));
    }

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository(
            OAuth2AuthorizedClientService authorizedClientService) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
    }

    /**
     * This is where we actually configure the Security-API to talk to Discord
     *
     * @return The ClientRegistration for Discord
     */
    private ClientRegistration googleClientRegistration(Config config) {
        return ClientRegistration.withRegistrationId("Discord")
            .clientName(config.getClientName())
            .clientId(config.getClientId())
            .clientSecret(config.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .scope("identify")
            .authorizationUri("https://discord.com/api/oauth2/authorize")
            .userInfoUri("https://discordapp.com/api/users/@me")
            .userNameAttributeName("username")
            .tokenUri("https://discordapp.com/api/oauth2/token")
            .redirectUri(config.getRedirectPath())
            .build();
    }

    /**
     * Configures the Security-API which path's should be protected and generally what to do
     */
    @EnableWebSecurity
    public static class OAuth2LoginSecurityConfig extends VaadinWebSecurityConfigurerAdapter {

        @Override
        public void configure(WebSecurity web) throws Exception {
            super.configure(web);
            web.ignoring().antMatchers("/rest/api/**");
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.authorizeRequests().antMatchers("/rest/api/**").anonymous();
            http.oauth2Login();
            http.logout()
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                .clearAuthentication(true);

            // Enables Vaadin to load Server Resources
            super.configure(http);
        }
    }
}
