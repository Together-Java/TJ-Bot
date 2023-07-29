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
    private final RateLimiter<Long> rateLimiter;

    public JShellEval(JShellConfig config) {
        this.api = new JShellApi(new ObjectMapper(), config.baseUrl());
        this.renderer = new ResultRenderer();

        this.rateLimiter = new RateLimiter<>(Duration.ofSeconds(config.rateLimitWindowSeconds()),
                config.rateLimitRequestsInWindow());
    }

    public JShellApi getApi() {
        return api;
    }

    public MessageEmbed evaluateAndRespond(User user, String code, boolean showCode,
            boolean oneOffSession) throws RequestFailedException {
        JShellResult result;
        if (oneOffSession) {
            MessageEmbed rateLimitedMessage = wasRateLimited(user, Instant.now());
            if (rateLimitedMessage != null) {
                return rateLimitedMessage;
            }
            result = api.evalOnce(code);
        } else {
            result = api.evalSession(code, user.getId());
        }

        return renderer
            .renderToEmbed(user, showCode ? code : null, !oneOffSession, result, new EmbedBuilder())
            .build();
    }

    @Nullable
    private MessageEmbed wasRateLimited(User user, Instant checkTime) {
        if (rateLimiter.allowRequest(user.getIdLong(), checkTime)) {
            return null;
        }

        String nextAllowedTime = TimeFormat.RELATIVE
            .format(rateLimiter.nextAllowedRequestTime(user.getIdLong(), checkTime));
        return new EmbedBuilder().setAuthor(user.getName() + "'s result")
            .setDescription(
                    "You are currently rate-limited. Please try again " + nextAllowedTime + ".")
            .setColor(Colors.ERROR_COLOR)
            .build();
    }
}
