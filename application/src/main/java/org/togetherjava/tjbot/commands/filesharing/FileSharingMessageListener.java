package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent help messages and uploads them to a share service if the message
 * contains a file with the given extension in the
 * {@link FileSharingMessageListener#extensionFilter}.
 */
public class FileSharingMessageListener extends MessageReceiverAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SHARE_API = "https://api.github.com/gists";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final String gistApiKey;
    private final Set<String> extensionFilter = Set.of("txt", "java", "gradle", "xml", "kt", "json",
            "fxml", "css", "c", "h", "cpp", "py", "yml");

    private final Predicate<String> isStagingChannelName;
    private final Predicate<String> isOverviewChannelName;


    public FileSharingMessageListener(@NotNull Config config) {
        super(Pattern.compile(".*"));

        gistApiKey = config.getGistApiKey();
        isStagingChannelName = Pattern.compile(config.getHelpSystem().getStagingChannelPattern())
            .asMatchPredicate();
        isOverviewChannelName = Pattern.compile(config.getHelpSystem().getOverviewChannelPattern())
            .asMatchPredicate();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || event.isWebhookMessage()) {
            return;
        }

        if (!isHelpThread(event)) {
            return;
        }


        List<Message.Attachment> attachments = event.getMessage()
            .getAttachments()
            .stream()
            .filter(this::isAttachmentRelevant)
            .toList();

        CompletableFuture.runAsync(() -> processAttachments(event, attachments));
    }

    private boolean isAttachmentRelevant(@NotNull Message.Attachment attachment) {
        String extension = attachment.getFileExtension();
        if (extension == null) {
            return false;
        }
        return extensionFilter.contains(extension);
    }


    private void processAttachments(@NotNull MessageReceivedEvent event,
            @NotNull List<Message.Attachment> attachments) {

        Map<String, GistFile> filesAsJson = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (Message.Attachment attachment : attachments) {
            CompletableFuture<Void> task = attachment.retrieveInputStream()
                .thenApply(this::readAttachment)
                .thenAccept(
                        content -> filesAsJson.put(getNameOf(attachment), new GistFile(content)));

            tasks.add(task);
        }

        tasks.forEach(CompletableFuture::join);

        GistFiles files = new GistFiles(filesAsJson);
        GistRequest request = new GistRequest(event.getAuthor().getName(), false, files);
        String url = uploadToGist(request);
        sendResponse(event, url);
    }

    private @NotNull String readAttachment(@NotNull InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private @NotNull String getNameOf(@NotNull Message.Attachment attachment) {
        String fileName = attachment.getFileName();
        String fileExtension = attachment.getFileExtension();

        if (fileExtension == null || fileExtension.equals("txt")) {
            fileExtension = "java";
        } else if (fileExtension.equals("fxml")) {
            fileExtension = "xml";
        }

        fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "." + fileExtension;
        return fileName;
    }

    private @NotNull String uploadToGist(@NotNull GistRequest jsonRequest) {
        String body;
        try {
            body = JSON.writeValueAsString(jsonRequest);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Attempting to upload a file to gist, but unable to create the JSON request.",
                    e);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SHARE_API))
            .header("Accept", "application/json")
            .header("Authorization", "token " + gistApiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> apiResponse;
        try {
            apiResponse = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Attempting to upload a file to gist, but the request got interrupted.", e);
        }

        int statusCode = apiResponse.statusCode();

        if (statusCode < HttpURLConnection.HTTP_OK
                || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw new IllegalStateException("Gist API unexpected response: " + apiResponse.body());
        }

        GistResponse gistResponse;
        try {
            gistResponse = JSON.readValue(apiResponse.body(), GistResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Attempting to upload file to gist, but unable to parse its JSON response.", e);
        }
        return gistResponse.getHtmlUrl();
    }

    private void sendResponse(@NotNull MessageReceivedEvent event, @NotNull String url) {
        Message message = event.getMessage();
        String messageContent =
                "I uploaded your attachments as **gist**. That way, they are easier to read for everyone, especially mobile users 👍";

        message.reply(messageContent).setActionRow(Button.link(url, "gist")).queue();
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
