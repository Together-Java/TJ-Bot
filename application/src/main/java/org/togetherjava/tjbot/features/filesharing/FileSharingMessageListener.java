package org.togetherjava.tjbot.features.filesharing;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.kohsuke.github.GHGist;
import org.kohsuke.github.GHGistBuilder;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent help messages and uploads them to a share service if the message
 * contains a file with the given extension in the
 * {@link FileSharingMessageListener#extensionFilter}.
 */
public final class FileSharingMessageListener extends MessageReceiverAdapter
        implements UserInteractor {
    private static final Logger logger = LoggerFactory.getLogger(FileSharingMessageListener.class);

    private final ComponentIdInteractor componentIdInteractor =
            new ComponentIdInteractor(getInteractionType(), getName());

    private final String gistApiKey;
    private final Set<String> extensionFilter = Set.of("txt", "java", "gradle", "xml", "kt", "json",
            "fxml", "css", "c", "h", "cpp", "py", "yml");

    private final Predicate<String> isHelpForumName;
    private final Predicate<String> isSoftModRole;

    /**
     * Creates a new instance.
     * 
     * @param config used to get api key and channel names.
     * @see org.togetherjava.tjbot.features.Features
     */
    public FileSharingMessageListener(Config config) {
        gistApiKey = config.getGistApiKey();
        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();
        isSoftModRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        User author = event.getAuthor();

        if (author.isBot() || event.isWebhookMessage() || !isHelpThread(event)) {
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
                logger.error(
                        "Unknown error while processing attachments. Channel: {}, Author: {}, Message ID: {}.",
                        event.getChannel().getName(), author.getId(), event.getMessageId(), e);
            }
        });
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

        try {
            new GitHubBuilder().withOAuthToken(gistApiKey).build().getGist(gistId).delete();

            event.getMessage().delete().queue();
        } catch (IOException e) {
            logger.error("Failed to delete gist with id {}", gistId);
        }
    }

    private boolean isAttachmentRelevant(Message.Attachment attachment) {
        String extension = attachment.getFileExtension();
        if (extension == null) {
            return false;
        }
        return extensionFilter.contains(extension);
    }

    private void processAttachments(MessageReceivedEvent event,
            List<Message.Attachment> attachments) throws IOException {
        GHGistBuilder gistBuilder = new GitHubBuilder().withOAuthToken(gistApiKey)
            .build()
            .createGist()
            .public_(true)
            .description("Uploaded by " + event.getAuthor().getAsTag());

        attachments.forEach(attachment -> attachment.getProxy()
            .download()
            .thenApply(this::readAttachment)
            .thenAccept(content -> gistBuilder.file(getNameOf(attachment), content))
            .join());

        GHGist gist = gistBuilder.create();
        sendResponse(event, gist.getHtmlUrl().toString(), gist.getGistId());
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

    private void sendResponse(MessageReceivedEvent event, String url, String gistId) {
        Message message = event.getMessage();
        String messageContent =
                "I uploaded your attachments as **gist**. That way, they are easier to read for everyone, especially mobile users 👍";

        Button gist = Button.link(url, "Gist");

        Button delete = Button.danger(
                componentIdInteractor.generateComponentId(message.getAuthor().getId(), gistId),
                "Dismiss");

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
    public void onSelectMenuSelection(SelectMenuInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }
}
