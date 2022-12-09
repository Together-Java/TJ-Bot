package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.commands.UserInteractionType;
import org.togetherjava.tjbot.commands.UserInteractor;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.commands.componentids.ComponentIdInteractor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent help messages and uploads them to a share service if the message
 * contains a file with the given extension in the
 * {@link FileSharingMessageListener#extensionFilter}.
 */
public class FileSharingMessageListener extends MessageReceiverAdapter implements UserInteractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSharingMessageListener.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ComponentIdInteractor componentIdInteractor =
            new ComponentIdInteractor(getInteractionType(), getName());

    private static final String SHARE_API = "https://api.github.com/gists";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final String gistApiKey;
    private final Set<String> extensionFilter = Set.of("txt", "java", "gradle", "xml", "kt", "json",
            "fxml", "css", "c", "h", "cpp", "py", "yml");

    private final Predicate<String> isHelpForumName;
    private final Predicate<String> isSoftModRole;

    /**
     * Creates a new instance.
     * 
     * @param config used to get api key and channel names.
     * @see org.togetherjava.tjbot.commands.Features
     */
    public FileSharingMessageListener(Config config) {
        super(Pattern.compile(".*"));

        gistApiKey = config.getGistApiKey();
        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();
        isSoftModRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
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

        if (attachments.isEmpty()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                processAttachments(event, attachments);
            } catch (Exception e) {
                LOGGER.error(
                        "Unknown error while processing attachments. Channel: {}, Author: {}, Message ID: {}.",
                        event.getChannel().getName(), author.getId(), event.getMessageId(), e);
            }
        });
    }

    private boolean isAttachmentRelevant(Message.Attachment attachment) {
        String extension = attachment.getFileExtension();
        if (extension == null) {
            return false;
        }
        return extensionFilter.contains(extension);
    }

    private void processAttachments(MessageReceivedEvent event,
            List<Message.Attachment> attachments) {

        Map<String, GistFile> nameToFile = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (Message.Attachment attachment : attachments) {
            CompletableFuture<Void> task = attachment.getProxy()
                .download()
                .thenApply(this::readAttachment)
                .thenAccept(
                        content -> nameToFile.put(getNameOf(attachment), new GistFile(content)));

            tasks.add(task);
        }

        tasks.forEach(CompletableFuture::join);

        GistFiles files = new GistFiles(nameToFile);
        GistRequest request = new GistRequest(event.getAuthor().getName(), false, files);
        GistResponse response = uploadToGist(request);
        String url = response.getHtmlUrl();
        String gistId = response.getGistId();
        sendResponse(event, url, gistId);
    }

    private String readAttachment(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getNameOf(Message.Attachment attachment) {
        String fileName = attachment.getFileName();
        String fileExtension = attachment.getFileExtension();

        if (fileExtension == null || fileExtension.equals("txt")) {
            fileExtension = "java";
        } else if (fileExtension.equals("fxml")) {
            fileExtension = "xml";
        }

        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex != -1) {
            fileName = fileName.substring(0, extensionIndex);
        }

        fileName += "." + fileExtension;

        return fileName;
    }

    private GistResponse uploadToGist(GistRequest jsonRequest) {
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
            throw new IllegalStateException("Gist API unexpected response: %s. Request JSON: %s"
                .formatted(apiResponse.body(), body));
        }

        GistResponse gistResponse;
        try {
            gistResponse = JSON.readValue(apiResponse.body(), GistResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Attempting to upload file to gist, but unable to parse its JSON response.", e);
        }
        return gistResponse;
    }

    private void sendResponse(MessageReceivedEvent event, String url, String gistId) {
        Message message = event.getMessage();
        String messageContent =
                "I uploaded your attachments as **gist**. That way, they are easier to read for everyone, especially mobile users 👍";

        Button gist = Button.link(url, "gist");

        Button delete = Button.danger(
                componentIdInteractor.generateComponentId(message.getAuthor().getId(), gistId),
                Emoji.fromUnicode("🗑️"));

        message.reply(messageContent).setActionRow(gist, delete).queue();
    }

    private boolean isHelpThread(MessageReceivedEvent event) {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return false;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        String rootChannelName = thread.getParentChannel().getName();
        return isHelpForumName.test(rootChannelName);
    }

    private void deleteGist(String gistId) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SHARE_API + "/" + gistId))
            .header("Accept", "application/json")
            .header("Authorization", "token " + gistApiKey)
            .DELETE()
            .build();

        HttpResponse<String> apiResponse;
        try {
            apiResponse = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Attempting to delete a gist, but the request got interrupted.", e);
        }

        int status = apiResponse.statusCode();
        if (status == 404) {
            String responseBody = apiResponse.body();
            LOGGER.warn("Gist API unexpected response while deleting gist: {}.", responseBody);
        }
    }

    @Override
    public String getName() {
        return "filesharing";
    }

    @Override
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
    }

    @Override
    public UserInteractionType getInteractionType() {
        return UserInteractionType.OTHER;
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        Member interactionUser = event.getMember();
        String gistAuthorId = args.get(0);
        boolean hasSoftModPermissions =
                interactionUser.getRoles().stream().map(Role::getName).anyMatch(isSoftModRole);

        if (!gistAuthorId.equals(interactionUser.getId()) && !hasSoftModPermissions) {
            event.reply("You do not have permission for this action.").setEphemeral(true).queue();
            return;
        }

        Message message = event.getMessage();
        List<Button> buttons = message.getButtons();
        event.editComponents(ActionRow.of(buttons.stream().map(Button::asDisabled).toList()))
            .queue();

        String gistId = args.get(1);
        deleteGist(gistId);
    }

    @Override
    public void onSelectMenuSelection(SelectMenuInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

}
