package org.togetherjava.tjbot.features.jshell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigpwned.jackson.modules.jdk17.sealedclasses.Jdk17SealedClassesModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;

import org.togetherjava.tjbot.config.JShellConfig;
import org.togetherjava.tjbot.features.jshell.backend.JShellApi;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.renderer.ResultRenderer;
import org.togetherjava.tjbot.features.utils.Colors;
import org.togetherjava.tjbot.features.utils.ConnectionFailedException;
import org.togetherjava.tjbot.features.utils.RateLimiter;
import org.togetherjava.tjbot.features.utils.RequestFailedException;

import javax.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * Provides a mid-ground between JDA and JShell API which can be used from many places in the bot,
 * including JShell commands and JShell code actions.
 */
public class JShellEval {
    private final String gistApiToken;
    private final JShellApi api;

    private final ResultRenderer renderer;
    private final RateLimiter rateLimiter;

    /**
     * Creates a JShell evaluation instance
     * 
     * @param config the JShell configuration to use
     * @param gistApiToken token of Gist api in case a JShell result is uploaded here
     */
    public JShellEval(JShellConfig config, String gistApiToken) {
        this.gistApiToken = gistApiToken;
        this.api = new JShellApi(new ObjectMapper().registerModule(new Jdk17SealedClassesModule()),
                config.baseUrl());
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
     * @param member the member, if null, will create a single use session
     * @param code the code
     * @param showCode if the original code should be displayed
     * @param startupScript if the startup script should be used or not
     * @return the response
     * @throws RequestFailedException if a http error happens
     * @throws ConnectionFailedException if the connection to the API couldn't be made at the first
     *         place
     */
    public MessageEmbed evaluateAndRespond(@Nullable Member member, String code, boolean showCode,
            boolean startupScript) throws RequestFailedException, ConnectionFailedException {
        MessageEmbed rateLimitedMessage = wasRateLimited(member, Instant.now());
        if (rateLimitedMessage != null) {
            return rateLimitedMessage;
        }
        JShellResult result;
        if (member == null) {
            result = api.evalOnce(code, startupScript);
        } else {
            result = api.evalSession(code, member.getId(), startupScript);
        }

        return renderer.render(gistApiToken, member, showCode, result);
    }

    @Nullable
    private MessageEmbed wasRateLimited(@Nullable Member member, Instant checkTime) {
        if (rateLimiter.allowRequest(checkTime)) {
            return null;
        }

        String nextAllowedTime =
                TimeFormat.RELATIVE.format(rateLimiter.nextAllowedRequestTime(checkTime));
        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setDescription(
                    "You are currently rate-limited. Please try again " + nextAllowedTime + ".")
            .setColor(Colors.ERROR_COLOR);
        if (member != null) {
            embedBuilder.setAuthor(member.getEffectiveName() + "'s result");
        }
        return embedBuilder.build();
    }
}
