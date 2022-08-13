package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent help messages and uploads them to a share service if the message
 * contains a file with the given extension in the
 * {@link ShareLongAttachmentsMessageListener#extensionFilter}
 */
public final class ShareLongAttachmentsMessageListener extends MessageReceiverAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ShareLongAttachmentsMessageListener.class);

    private final Set<String> extensionFilter = Set.of("txt", "java", "gradle", "xml", "kt", "json",
            "fxml", "css", "c", "h", "cpp", "py", "yml");

    private final String API_KEY;
    private static final String SHARE_API = "https://api.github.com/gists";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final Predicate<String> isStagingChannelName;
    private final Predicate<String> isOverviewChannelName;

    public ShareLongAttachmentsMessageListener(@NotNull Config config) {
        super(Pattern.compile(".*"));

        API_KEY = config.getShareApiKey();
        isStagingChannelName = Pattern.compile(config.getHelpSystem().getStagingChannelPattern())
            .asMatchPredicate();
        isOverviewChannelName = Pattern.compile(config.getHelpSystem().getOverviewChannelPattern())
            .asMatchPredicate();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        if (!isHelpThread(event)) {
            return;
        }

        event.getMessage().getAttachments().stream().filter(attachment -> {
            String fileExtension = attachment.getFileExtension();
            if (fileExtension == null) {
                return false;
            }
            return extensionFilter.contains(fileExtension);
        }).forEach(attachment -> processAttachment(event, attachment));
    }

    private void processAttachment(@NotNull MessageReceivedEvent event,
            @NotNull Message.Attachment attachment) {
        attachment.retrieveInputStream()
            .thenApply(this::readAttachment)
            .thenApply(content -> uploadToSharingService(event, attachment, content))
            .thenAccept(url -> sendResponse(event, attachment, url));
    }

    private @NotNull String readAttachment(@NotNull InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private @NotNull String uploadToSharingService(@NotNull MessageReceivedEvent event,
            @NotNull Message.Attachment attachment, @NotNull String content) {

        String fileName = attachment.getFileName();
        String fileExtension = attachment.getFileExtension();

        if (fileExtension == null || fileExtension.equals("txt")) {
            fileExtension = "java";
        } else if (fileExtension.equals("fxml")) {
            fileExtension = "xml";
        }

        fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "." + fileExtension;

        GistFiles files = new GistFiles(Map.of(fileName, new GistFile(content)));
        GistRequest jsonRequest = new GistRequest(event.getAuthor().getName(), false, files);

        String body;
        try {
            body = JSON.writeValueAsString(jsonRequest);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Couldn't parse json request!", e); // cause exception doesn't show in
                                                            // gradle run:
                                                            // https://github.com/Together-Java/TJ-Bot/issues/490
            throw new IllegalStateException("Couldn't parse json request!", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SHARE_API))
            .header("Accept", "application/json")
            .header("Authorization", "token " + API_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> apiResponse;
        try {
            apiResponse = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int statusCode = apiResponse.statusCode();

        if (statusCode < HttpURLConnection.HTTP_OK
                || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            LOGGER.warn("Gist API unexpected response: {}", apiResponse.body()); // cause exception
                                                                                 // doesn't show in
                                                                                 // gradle run:
                                                                                 // https://github.com/Together-Java/TJ-Bot/issues/490
            throw new IllegalStateException("Gist API unexpected response: " + apiResponse.body());
        }

        GistResponse gistResponse;
        try {
            gistResponse = JSON.readValue(apiResponse.body(), GistResponse.class);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Couldn't parse response!", e); // cause exception doesn't show in gradle
                                                        // run:
                                                        // https://github.com/Together-Java/TJ-Bot/issues/490
            throw new IllegalStateException("Couldn't parse response!", e);
        }
        return gistResponse.getHtmlUrl();
    }

    private void sendResponse(@NotNull MessageReceivedEvent event,
            @NotNull Message.Attachment attachment, @NotNull String url) {
        Message message = event.getMessage();
        String replyContent =
                "I uploaded your file as gist, it is much easier to read for everyone like that, especially for mobile users";
        String fileName = attachment.getFileName();

        message.reply(replyContent)
            .setActionRow(Button.of(ButtonStyle.LINK, url, fileName))
            .queue();
    }

    private boolean isHelpThread(@NotNull MessageReceivedEvent event) {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return false;
        }

        ThreadChannel thread = event.getThreadChannel();
        String rootChannelName = thread.getParentChannel().getName();
        return isStagingChannelName.test(rootChannelName)
                || isOverviewChannelName.test(rootChannelName);
    }

}
