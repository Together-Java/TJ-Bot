package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import io.mikael.urlbuilder.UrlBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.mathcommands.wolframalpha.api.QueryResult;
import org.togetherjava.tjbot.config.Config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Command to send a query to the <a href="https://www.wolframalpha.com/">Wolfram Alpha</a> API.
 * Renders its response as images.
 */
public final class WolframAlphaCommand extends SlashCommandAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WolframAlphaCommand.class);
    private static final String QUERY_OPTION = "query";
    /**
     * WolframAlpha API endpoint to connect to.
     *
     * @see <a href=
     *      "https://products.wolframalpha.com/docs/WolframAlpha-API-Reference.pdf">WolframAlpha API
     *      Reference</a>.
     */
    private static final String API_ENDPOINT = "http://api.wolframalpha.com/v2/query";
    /**
     * WolframAlpha API endpoint for regular users (web frontend).
     */
    private static final String USER_API_ENDPOINT = "https://www.wolframalpha.com/input";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final String appId;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     */
    public WolframAlphaCommand(@NotNull Config config) {
        super("wolfram-alpha", "Renders mathematical queries using WolframAlpha",
                SlashCommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, QUERY_OPTION, "the query to send to WolframAlpha",
                true);
        appId = config.getWolframAlphaAppId();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        // The API calls take a bit
        event.deferReply().queue();

        String query = event.getOption(QUERY_OPTION).getAsString();

        MessageEmbed uriEmbed = new EmbedBuilder()
            .setTitle(query + "- Wolfram|Alpha",
                    UrlBuilder.fromString(USER_API_ENDPOINT)
                        .addParameter("i", query)
                        .toUri()
                        .toString())
            .build();

        WebhookMessageUpdateAction<Message> action = event.getHook().editOriginalEmbeds(uriEmbed);

        HttpRequest request = HttpRequest
            .newBuilder(UrlBuilder.fromString(API_ENDPOINT)
                .addParameter("appid", appId)
                .addParameter("format", "image,plaintext")
                .addParameter("input", query)
                .toUri())
            .GET()
            .build();

        Optional<HttpResponse<String>> maybeResponse = getResponse(request, action);
        if (maybeResponse.isEmpty()) {
            return;
        }
        HttpResponse<String> response = maybeResponse.orElseThrow();
        Optional<QueryResult> maybeResult = WolframAlphaCommandUtils.parseQuery(response, action);
        if (maybeResult.isEmpty()) {
            return;
        }
        QueryResult result = maybeResult.orElseThrow();
        action = action.setContent("Computed in:" + result.getTiming());
        action.setContent(switch (ResultStatus.getResultStatus(result)) {
            case ERROR -> WolframAlphaCommandUtils.handleError(result);
            case NOT_SUCCESS -> WolframAlphaCommandUtils.handleMisunderstoodQuery(result);
            case SUCCESS -> "Check the above link for the complete results\n"
                    + (result.getTimedOutPods().isEmpty() ? ""
                            : "Some pods have timed out. Visit the URI")
                    + "\n"
                    + WolframAlphaCommandUtils.handleSuccessfulResult(result, action, uriEmbed);
        }).queue();
    }

    private static @NotNull Optional<HttpResponse<String>> getResponse(@NotNull HttpRequest request,
            @NotNull WebhookMessageUpdateAction<Message> action) {
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            action.setContent("Unable to get a response from WolframAlpha API").queue();
            LOGGER.warn("Could not get the response from the server", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            action.setContent("Connection to WolframAlpha was interrupted").queue();
            LOGGER.warn("Connection to WolframAlpha was interrupted", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }

        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            action.setContent("The response' status code was incorrect").queue();
            LOGGER.warn("Unexpected status code: Expected: {} Actual: {}",
                    HttpURLConnection.HTTP_OK, response.statusCode());
            return Optional.empty();
        }
        return Optional.of(response);
    }

}
