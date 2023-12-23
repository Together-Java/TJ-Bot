package org.togetherjava.tjbot.features.jshell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigpwned.jackson.modules.jdk17.sealedclasses.Jdk17SealedClassesModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.TimeFormat;

import org.togetherjava.tjbot.config.JShellConfig;
import org.togetherjava.tjbot.features.jshell.backend.JShellApi;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.renderer.RenderResult;
import org.togetherjava.tjbot.features.jshell.renderer.ResultRenderer;
import org.togetherjava.tjbot.features.utils.Colors;
import org.togetherjava.tjbot.features.utils.ConnectionFailedException;
import org.togetherjava.tjbot.features.utils.RateLimiter;
import org.togetherjava.tjbot.features.utils.RequestFailedException;

import javax.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Provides a mid-ground between JDA and JShell API which can be used from many places in the bot,
 * including JShell commands and JShell code actions.
 */
public class JShellEval {
    private final JShellApi api;

    private final ResultRenderer renderer;
    private final RateLimiter rateLimiter;

    /**
     * Creates a JShell evaluation instance
     * 
     * @param config the JShell configuration to use
     */
    public JShellEval(JShellConfig config) {
        this.api = new JShellApi(new ObjectMapper().registerModule(new Jdk17SealedClassesModule()), config.baseUrl());
        this.renderer = new ResultRenderer();

        this.rateLimiter = new RateLimiter(Duration.ofSeconds(config.rateLimitWindowSeconds()),
                config.rateLimitRequestsInWindow());
    }

    public JShellApi getApi() {
        return api;
    }

    /**
     * Evaluate code and return a message containing the response.
     *
     * @param user the user, if null, will create a single use session
     * @param code the code
     * @param showCode if the original code should be displayed
     * @param startupScript if the startup script should be used or not
     * @return the response
     * @throws RequestFailedException if a http error happens
     * @throws ConnectionFailedException if the connection to the API couldn't be made at the first
     *         place
     */
    public RenderResult evaluateAndRespond(@Nullable Member user, String code, boolean showCode,
                                           boolean startupScript) throws RequestFailedException, ConnectionFailedException {
        MessageEmbed rateLimitedMessage = wasRateLimited(user, Instant.now());
        if (rateLimitedMessage != null) {
            return new RenderResult.EmbedResult(List.of(rateLimitedMessage));
        }
        JShellResult result;
        if (user == null) {
            result = api.evalOnce(code, startupScript);
        } else {
            result = api.evalSession(code, user.getId(), startupScript);
        }

        return renderer.render(user, showCode, result);
    }

    @Nullable
    private MessageEmbed wasRateLimited(@Nullable Member user, Instant checkTime) {
        if (rateLimiter.allowRequest(checkTime)) {
            return null;
        }

        String nextAllowedTime =
                TimeFormat.RELATIVE.format(rateLimiter.nextAllowedRequestTime(checkTime));
        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setDescription(
                    "You are currently rate-limited. Please try again " + nextAllowedTime + ".")
            .setColor(Colors.ERROR_COLOR);
        if (user != null) {
            embedBuilder.setAuthor(user.getEffectiveName() + "'s result");
        }
        return embedBuilder.build();
    }
}
