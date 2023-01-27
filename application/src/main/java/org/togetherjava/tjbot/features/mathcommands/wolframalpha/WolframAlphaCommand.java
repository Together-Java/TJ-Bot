package org.togetherjava.tjbot.features.mathcommands.wolframalpha;

import io.mikael.urlbuilder.UrlBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.FileUpload;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Command to send a query to the <a href="https://www.wolframalpha.com/">Wolfram Alpha</a> API.
 * Renders its response as images.
 */
public final class WolframAlphaCommand extends SlashCommandAdapter {
    private static final String QUERY_OPTION = "query";
    /**
     * WolframAlpha API endpoint to connect to.
     *
     * @see <a href=
     *      "https://products.wolframalpha.com/docs/WolframAlpha-API-Reference.pdf">WolframAlpha API
     *      Reference</a>.
     */
    private static final String API_ENDPOINT = "http://api.wolframalpha.com/v2/query";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final String appId;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     */
    public WolframAlphaCommand(Config config) {
        super("wolfram-alpha", "Renders mathematical queries using WolframAlpha",
                CommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, QUERY_OPTION, "the query to send to WolframAlpha",
                true);
        appId = config.getWolframAlphaAppId();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String query = event.getOption(QUERY_OPTION).getAsString();
        WolframAlphaHandler handler = new WolframAlphaHandler(query);

        // The API call takes a bit
        event.deferReply().queue();

        // Send query
        HttpRequest request = HttpRequest
            .newBuilder(UrlBuilder.fromString(API_ENDPOINT)
                .addParameter("appid", appId)
                .addParameter("format", "image,plaintext")
                .addParameter("input", query)
                .toUri())
            .GET()
            .build();

        CompletableFuture<HttpResponse<String>> apiResponse =
                CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        // Parse and respond
        apiResponse.thenApply(handler::handleApiResponse)
            .thenAccept(response -> sendResponse(response, event));
    }

    private static void sendResponse(WolframAlphaHandler.HandlerResponse response,
            IDeferrableCallback event) {
        List<FileUpload> files = response.attachments()
            .stream()
            .map(attachment -> FileUpload.fromData(attachment.data(), attachment.name()))
            .toList();

        event.getHook().editOriginalEmbeds(response.embeds()).setFiles(files).queue();
    }
}
