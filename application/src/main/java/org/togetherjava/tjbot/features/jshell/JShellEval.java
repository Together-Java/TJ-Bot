package org.togetherjava.tjbot.features.jshell;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.TimeFormat;

import org.togetherjava.tjbot.config.JShellConfig;
import org.togetherjava.tjbot.features.jshell.backend.JShellApi;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.render.Colors;
import org.togetherjava.tjbot.features.jshell.render.ResultRenderer;
import org.togetherjava.tjbot.features.utils.RateLimiter;
import org.togetherjava.tjbot.features.utils.RequestFailedException;

import javax.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

public class JShellEval {
    private final JShellApi api;

    private final ResultRenderer renderer;
    private final RateLimiter rateLimiter;

    public JShellEval(JShellConfig config) {
        this.api = new JShellApi(new ObjectMapper(), config.baseUrl());
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
     * @return the response
     * @throws RequestFailedException if a http error happens
     */
    public MessageEmbed evaluateAndRespond(@Nullable User user, String code, boolean showCode)
            throws RequestFailedException {
        MessageEmbed rateLimitedMessage = wasRateLimited(user, Instant.now());
        if (rateLimitedMessage != null) {
            return rateLimitedMessage;
        }
        JShellResult result;
        if (user == null) {
            result = api.evalOnce(code);
        } else {
            result = api.evalSession(code, user.getId());
        }

        return renderer
            .renderToEmbed(user, showCode ? code : null, user != null, result, new EmbedBuilder())
            .build();
    }

    @Nullable
    private MessageEmbed wasRateLimited(@Nullable User user, Instant checkTime) {
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
            embedBuilder.setAuthor(user.getName() + "'s result");
        }
        return embedBuilder.build();
    }
}
