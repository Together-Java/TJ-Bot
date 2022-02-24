package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import io.mikael.urlbuilder.UrlBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;

public final class WolframAlphaCommand extends SlashCommandAdapter {

    private final String wolframAlphaAppId;

    public WolframAlphaCommand(Config config) {
        super("wolfram-alpha", "Renders mathematical queries using WolframAlpha",
                SlashCommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, Constants.QUERY_OPTION,
                "the query to send to WolframAlpha", true);
        wolframAlphaAppId = config.getWolframAlphaAppId();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {

        // The processing takes some time
        event.deferReply().queue();

        String query =
                Objects.requireNonNull(event.getOption(Constants.QUERY_OPTION)).getAsString();

        MessageEmbed uriEmbed = new EmbedBuilder()
            .setTitle(query + "- Wolfram|Alpha",
                    UrlBuilder.fromString(Constants.USER_ENDPOINT)
                        .addParameter("i", query)
                        .toUri()
                        .toString())
            .setDescription(
                    "Wolfram|Alpha brings expert-level knowledge and capabilities to the broadest possible range of people-spanning all professions and education levels.")
            .build();

        WebhookMessageUpdateAction<Message> action =
                event.getHook().editOriginal("").setEmbeds(uriEmbed);

        HttpRequest request = HttpRequest
            .newBuilder(UrlBuilder.fromString(Constants.API_ENDPOINT)
                .addParameter("appid", wolframAlphaAppId)
                .addParameter("format", "image,plaintext")
                .addParameter("input", query)
                .toUri())
            .GET()
            .build();

        Optional<HttpResponse<String>> optResponse = getResponse(request, action);
        if (optResponse.isEmpty())
            return;
        HttpResponse<String> response = optResponse.get();
        Optional<QueryResult> optResult = WolframAlphaCommandUtils.parseQuery(response, action);
        if (optResult.isEmpty())
            return;
        QueryResult result = optResult.get();
        action = action.setContent("Computed in:" + result.getTiming());
        action.setContent(switch (ResultStatus.getResultStatus(result)) {

            case ERROR -> WolframAlphaCommandUtils.handleError(result);

            case NOT_SUCCESS -> WolframAlphaCommandUtils.handleMisunderstoodQuery(result);

            case SUCCESS -> "Here are the results of your query, Check the link for the complete results\n"
                    + (result.getTimedOutPods().isEmpty() ? ""
                            : "Some pods have timed out. Visit the URI")
                    + "\n"
                    + WolframAlphaCommandUtils.handleSuccessfulResult(result, action, uriEmbed);
        }).queue();
    }

    private @NotNull Optional<HttpResponse<String>> getResponse(@NotNull HttpRequest request,
            @NotNull WebhookMessageUpdateAction<Message> action) {
        HttpResponse<String> response;
        try {
            response = Constants.client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            action.setContent("Unable to get a response from WolframAlpha API").queue();
            Constants.logger.warn("Could not get the response from the server", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            action.setContent("Connection to WolframAlpha was interrupted").queue();
            Constants.logger.warn("Connection to WolframAlpha was interrupted", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }

        if (response.statusCode() != Constants.HTTP_STATUS_CODE_OK) {
            action.setContent("The response' status code was incorrect").queue();
            Constants.logger.warn("Unexpected status code: Expected: {} Actual: {}",
                    Constants.HTTP_STATUS_CODE_OK, response.statusCode());
            return Optional.empty();
        }
        return Optional.of(response);
    }

}
