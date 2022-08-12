package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emoji;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ShareLongAttachmentsMessageListener extends MessageReceiverAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger logger =
            LoggerFactory.getLogger(ShareLongAttachmentsMessageListener.class);

    private final Set<String> extensions = Set.of("txt", "java", "gradle", "xml", "kt", "json",
            "fxml", "css", "c", "h", "cpp", "py", "yml");

    private static final String API_KEY = "< placeholder >";
    private static final String API = "https://api.github.com/gists";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final Predicate<String> isStagingChannelName;
    private final Predicate<String> isOverviewChannelName;

    public ShareLongAttachmentsMessageListener(@NotNull Config config) {
        super(Pattern.compile(".*"));

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

        event.getMessage()
            .getAttachments()
            .stream()
            .filter(attachment -> extensions.contains(attachment.getFileExtension()))
            .forEach(attachment -> processAttachment(event, attachment));
    }

    private void processAttachment(MessageReceivedEvent event, Message.Attachment attachment) {
        attachment.retrieveInputStream()
            .thenApply(this::readAttachment)
            .thenApply(content -> uploadToSharingService(event, attachment, content))
            .thenAccept(response -> sendMessage(event, attachment, response));
    }

    private String readAttachment(@NotNull InputStream stream) {
        try {
            byte[] bytes = stream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private HttpResponse<String> uploadToSharingService(MessageReceivedEvent event,
            Message.Attachment attachment, String content) {
        GistFile file = new GistFile();
        file.setContent(content);

        String parsedContent;
        try {
            parsedContent = JSON.writeValueAsString(file);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Couldn't parse content!", e);
        }

        String fileName = attachment.getFileName();
        String fileExtension = attachment.getFileExtension();

        if (fileExtension == null || fileExtension.equals("txt")) {
            fileExtension = "java";
        } else if (fileExtension.equals("fxml")) {
            fileExtension = "xml";
        }

        fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "." + fileExtension;

        String body = """
                {
                  "description": "%s",
                  "public": false,
                  "files": {
                      "%s": %s
                  }
                }""".formatted(event.getAuthor(), fileName, parsedContent);


        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API))
            .header("Accept", "application/json")
            .header("Authorization", "token " + API_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessage(MessageReceivedEvent event, Message.Attachment attachment,
            HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            logger.warn("Gist API unexpected response: " + response.body());
            throw new IllegalStateException("Gist API unexpected response: " + response.body());
        }

        Message message = event.getMessage();
        String fileName = attachment.getFileName();

        GistResponse gistResponse = null;
        try {
            gistResponse = JSON.readValue(response.body(), GistResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Couldn't parse response...", e);
        }

        String url = gistResponse.getHtmlUrl();

        message.reply(
                "I uploaded your file as gist, it is much easier to read for everyone like that especially for mobile users!")
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
