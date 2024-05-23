package org.togetherjava.tjbot.features.tags;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.moderation.audit.ModAuditLogWriter;

import javax.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Implements the {@code /tag-manage} command which allows management of tags, such as creating,
 * editing or deleting them. Available subcommands are:
 * <ul>
 * <li>{@code raw}</li>
 * <li>{@code create}</li>
 * <li>{@code create-with-message}</li>
 * <li>{@code edit}</li>
 * <li>{@code edit-with-message}</li>
 * <li>{@code delete}</li>
 * </ul>
 * <p>
 * Tags can be added by using {@link TagManageCommand} and a list of all tags is available using
 * {@link TagsCommand}.
 */
public final class TagManageCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TagManageCommand.class);
    static final String ID_OPTION = "id";
    private static final String ID_DESCRIPTION = "the id of the tag";
    static final String CONTENT_OPTION = "content";
    private static final String CONTENT_DESCRIPTION = "the content of the tag";
    static final String MESSAGE_ID_OPTION = "message-id";
    private static final String MESSAGE_ID_DESCRIPTION = "the id of the message to refer to";

    // "Edited tag **ask**"
    private static final String LOG_EMBED_DESCRIPTION = "%s tag **%s**";

    private static final String CONTENT_FILE_NAME = "content.md";
    private static final String NEW_CONTENT_FILE_NAME = "new_content.md";
    private static final String PREVIOUS_CONTENT_FILE_NAME = "previous_content.md";

    private static final String UNABLE_TO_GET_CONTENT_MESSAGE = "Was unable to retrieve content";

    private final TagSystem tagSystem;

    private final ModAuditLogWriter modAuditLogWriter;

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     * @param modAuditLogWriter to log tag changes for audition
     */
    public TagManageCommand(TagSystem tagSystem, ModAuditLogWriter modAuditLogWriter) {
        super("tag-manage", "Provides commands to manage all tags", CommandVisibility.GUILD);

        this.tagSystem = tagSystem;

        this.modAuditLogWriter = modAuditLogWriter;

        // TODO Think about adding a "Are you sure"-dialog to 'edit', 'edit-with-message' and
        // 'delete'
        getData().addSubcommands(new SubcommandData(Subcommand.RAW.name,
                "View the raw content of a tag, without Discord interpreting any of its content")
            .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true),
                new SubcommandData(Subcommand.CREATE.name, "Creates a new tag")
                    .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true)
                    .addOption(OptionType.STRING, CONTENT_OPTION, CONTENT_DESCRIPTION, true),
                new SubcommandData(Subcommand.CREATE_WITH_MESSAGE.name,
                        "Creates a new tag. Content is retrieved from the given message.")
                    .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true)
                    .addOption(OptionType.STRING, MESSAGE_ID_OPTION, MESSAGE_ID_DESCRIPTION, true),
                new SubcommandData(Subcommand.EDIT.name, "Edits a tag, the old content is replaced")
                    .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true)
                    .addOption(OptionType.STRING, CONTENT_OPTION, CONTENT_DESCRIPTION, true),
                new SubcommandData(Subcommand.EDIT_WITH_MESSAGE.name,
                        "Edits a tag, the old content is replaced. Content is retrieved from the given message.")
                    .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true)
                    .addOption(OptionType.STRING, MESSAGE_ID_OPTION, MESSAGE_ID_DESCRIPTION, true),
                new SubcommandData(Subcommand.DELETE.name, "Deletes a tag")
                    .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true));
    }

    private static void sendSuccessMessage(IReplyCallback event, String id, String actionVerb) {
        logger.info("User '{}' {} the tag with id '{}'.", event.getUser().getId(), actionVerb, id);

        event
            .replyEmbeds(new EmbedBuilder().setTitle("Success")
                .setDescription("Successfully %s tag '%s'.".formatted(actionVerb, id))
                .setColor(TagSystem.AMBIENT_COLOR)
                .build())
            .queue();
    }

    /**
     * Attempts to parse the given message id.
     * <p>
     * If the message id could not be parsed, because it is invalid, an error message is send to the
     * user.
     *
     * @param messageId the message id to parse
     * @param event the event to send messages with
     * @return the parsed message id, if successful
     */
    private static OptionalLong parseMessageIdAndHandle(String messageId, IReplyCallback event) {
        try {
            return OptionalLong.of(Long.parseLong(messageId));
        } catch (NumberFormatException e) {
            event
                .reply("The given message id '%s' is invalid, expected a number."
                    .formatted(messageId))
                .setEphemeral(true)
                .queue();
            return OptionalLong.empty();
        }
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (Subcommand.fromName(event.getSubcommandName())) {
            case RAW -> rawTag(event);
            case CREATE -> createTag(event);
            case CREATE_WITH_MESSAGE -> createTagWithMessage(event);
            case EDIT -> editTag(event);
            case EDIT_WITH_MESSAGE -> editTagWithMessage(event);
            case DELETE -> deleteTag(event);
            default -> throw new AssertionError(
                    "Unexpected subcommand '%s'".formatted(event.getSubcommandName()));
        }
    }

    private void rawTag(SlashCommandInteractionEvent event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        if (tagSystem.handleIsUnknownTag(id, event)) {
            return;
        }

        String content = tagSystem.getTag(id).orElseThrow();
        event.reply("")
            .addFiles(FileUpload.fromData(content.getBytes(StandardCharsets.UTF_8),
                    CONTENT_FILE_NAME))
            .queue();
    }

    private void createTag(CommandInteraction event) {
        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        handleAction(TagStatus.NOT_EXISTS, id -> tagSystem.putTag(id, content), event,
                Subcommand.CREATE, content);
    }

    private void createTagWithMessage(CommandInteraction event) {
        handleActionWithMessage(TagStatus.NOT_EXISTS, tagSystem::putTag, event,
                Subcommand.CREATE_WITH_MESSAGE);
    }

    private void editTag(CommandInteraction event) {
        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        handleAction(TagStatus.EXISTS, id -> tagSystem.putTag(id, content), event, Subcommand.EDIT,
                content);
    }

    private void editTagWithMessage(CommandInteraction event) {
        handleActionWithMessage(TagStatus.EXISTS, tagSystem::putTag, event,
                Subcommand.EDIT_WITH_MESSAGE);
    }

    private void deleteTag(CommandInteraction event) {
        handleAction(TagStatus.EXISTS, tagSystem::deleteTag, event, Subcommand.DELETE, null);
    }

    /**
     * Executes the given action on the tag id and sends a success message to the user.
     * <p>
     * If the tag status does not line up with the required status, an error message is send to the
     * user.
     *
     * @param requiredTagStatus the required status of the tag
     * @param idAction the action to perform on the id
     * @param event the event to send messages with, it must have an {@code id} option set
     * @param subcommand the subcommand to be executed
     * @param newContent the new content of the tag, or null if content is unchanged
     */
    private void handleAction(TagStatus requiredTagStatus, Consumer<? super String> idAction,
            CommandInteraction event, Subcommand subcommand, @Nullable String newContent) {

        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        if (isWrongTagStatusAndHandle(requiredTagStatus, id, event)) {
            return;
        }

        String previousContent =
                getTagContent(subcommand, id).orElse(UNABLE_TO_GET_CONTENT_MESSAGE);

        idAction.accept(id);
        sendSuccessMessage(event, id, subcommand.getActionVerb());

        Guild guild = Objects.requireNonNull(event.getGuild());
        logAction(subcommand, guild, event.getUser(), event.getTimeCreated(), id, newContent,
                previousContent);
    }

    /**
     * Executes the given action on the tag id and the content and sends a success message to the
     * user.
     * <p>
     * The content is retrieved by looking up the message with the id stored in the event.
     * <p>
     * If the tag status does not line up with the required status or a message with the given id
     * does not exist, an error message is send to the user.
     *
     * @param requiredTagStatus the required status of the tag
     * @param idAndContentAction the action to perform on the id and content
     * @param event the event to send messages with, it must have an {@code id} and
     *        {@code message-id} option set
     * @param subcommand the subcommand to be executed
     */
    private void handleActionWithMessage(TagStatus requiredTagStatus,
            BiConsumer<? super String, ? super String> idAndContentAction, CommandInteraction event,
            Subcommand subcommand) {

        String tagId = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        OptionalLong messageIdOpt = parseMessageIdAndHandle(
                Objects.requireNonNull(event.getOption(MESSAGE_ID_OPTION)).getAsString(), event);
        if (messageIdOpt.isEmpty()) {
            return;
        }
        long messageId = messageIdOpt.orElseThrow();
        if (isWrongTagStatusAndHandle(requiredTagStatus, tagId, event)) {
            return;
        }

        event.getMessageChannel().retrieveMessageById(messageId).queue(message -> {
            String previousContent =
                    getTagContent(subcommand, tagId).orElse(UNABLE_TO_GET_CONTENT_MESSAGE);

            idAndContentAction.accept(tagId, message.getContentRaw());
            sendSuccessMessage(event, tagId, subcommand.getActionVerb());

            Guild guild = Objects.requireNonNull(event.getGuild());
            logAction(subcommand, guild, event.getUser(), event.getTimeCreated(), tagId,
                    message.getContentRaw(), previousContent);

        }, failure -> {
            if (failure instanceof ErrorResponseException ex
                    && ex.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                event.reply("The message with id '%d' does not exist.".formatted(messageId))
                    .setEphemeral(true)
                    .queue();
                return;
            }

            logger.warn("Unable to retrieve the message with id '{}' for an unknown reason.",
                    messageId, failure);
            event
                .reply("Something unexpected went wrong trying to locate the message with id '%d'."
                    .formatted(messageId))
                .setEphemeral(true)
                .queue();
        });
    }

    /**
     * Gets the content of a tag.
     *
     * @param subcommand the subcommand to be executed
     * @param id the id of the tag to get its content
     * @return the content of the tag, if present
     */
    private Optional<String> getTagContent(Subcommand subcommand, String id) {
        if (Subcommand.SUBCOMMANDS_WITH_PREVIOUS_CONTENT.contains(subcommand)) {
            try {
                return tagSystem.getTag(id);
            } catch (NoSuchElementException e) {
                // NOTE Rare race condition, for example if another thread deleted the tag in the
                // meantime
                logger.warn("Tried to retrieve content of tag '{}', but the content doesn't exist.",
                        id);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns whether the status of the given tag is <b>not equal</b> to the required status.
     * <p>
     * If not, it sends an error message to the user.
     *
     * @param requiredTagStatus the required status of the tag
     * @param id the id of the tag to check
     * @param event the event to send messages with
     * @return whether the status of the given tag is <b>not equal</b> to the required status
     */
    private boolean isWrongTagStatusAndHandle(TagStatus requiredTagStatus, String id,
            IReplyCallback event) {
        return switch (requiredTagStatus) {
            case TagStatus.EXISTS -> tagSystem.handleIsUnknownTag(id, event);
            case TagStatus status when status == TagStatus.NOT_EXISTS && tagSystem.hasTag(id) -> {
                event.reply("The tag with id '%s' already exists.".formatted(id))
                    .setEphemeral(true)
                    .queue();
                yield true;
            }
            case TagStatus.NOT_EXISTS -> false;
        };
    }

    private void logAction(Subcommand subcommand, Guild guild, User author,
            TemporalAccessor triggeredAt, String id, @Nullable String newContent,
            @Nullable String previousContent) {

        List<ModAuditLogWriter.Attachment> attachments = new ArrayList<>();

        if (Subcommand.SUBCOMMANDS_WITH_NEW_CONTENT.contains(subcommand)) {
            if (newContent == null) {
                throw new IllegalArgumentException(
                        "newContent is null even though the subcommand should supply a value.");
            }

            String fileName = (subcommand == Subcommand.CREATE
                    || subcommand == Subcommand.CREATE_WITH_MESSAGE) ? CONTENT_FILE_NAME
                            : NEW_CONTENT_FILE_NAME;

            attachments.add(new ModAuditLogWriter.Attachment(fileName, newContent));

        }

        if (Subcommand.SUBCOMMANDS_WITH_PREVIOUS_CONTENT.contains(subcommand)) {
            if (previousContent == null) {
                throw new IllegalArgumentException(
                        "previousContent is null even though the subcommand should supply a value.");
            }

            attachments
                .add(new ModAuditLogWriter.Attachment(PREVIOUS_CONTENT_FILE_NAME, previousContent));
        }

        String title = switch (subcommand) {
            case CREATE -> "Tag-Manage Create";
            case CREATE_WITH_MESSAGE -> "Tag-Manage Create with message";
            case EDIT -> "Tag-Manage Edit";
            case EDIT_WITH_MESSAGE -> "Tag-Manage Edit with message";
            case DELETE -> "Tag-Manage Delete";
            default -> throw new IllegalArgumentException(
                    "The subcommand '%s' is not intended to be logged to the mod audit channel.");
        };

        modAuditLogWriter.write(title,
                LOG_EMBED_DESCRIPTION.formatted(subcommand.getActionVerb(), id), author,
                triggeredAt, guild, attachments.toArray(ModAuditLogWriter.Attachment[]::new));
    }

    private enum TagStatus {
        EXISTS,
        NOT_EXISTS
    }

    enum Subcommand {
        RAW("raw", ""),
        CREATE("create", "created"),
        CREATE_WITH_MESSAGE("create-with-message", "created"),
        EDIT("edit", "edited"),
        EDIT_WITH_MESSAGE("edit-with-message", "edited"),
        DELETE("delete", "deleted");

        private static final Set<Subcommand> SUBCOMMANDS_WITH_NEW_CONTENT =
                EnumSet.of(CREATE, CREATE_WITH_MESSAGE, EDIT, EDIT_WITH_MESSAGE);
        private static final Set<Subcommand> SUBCOMMANDS_WITH_PREVIOUS_CONTENT =
                EnumSet.of(EDIT, EDIT_WITH_MESSAGE, DELETE);

        private final String name;
        private final String actionVerb;

        Subcommand(String name, String actionVerb) {
            this.name = name;
            this.actionVerb = actionVerb;
        }

        String getName() {
            return name;
        }

        static Subcommand fromName(String name) {
            for (Subcommand subcommand : Subcommand.values()) {
                if (subcommand.name.equals(name)) {
                    return subcommand;
                }
            }
            throw new IllegalArgumentException(
                    "Subcommand with name '%s' is unknown".formatted(name));
        }

        String getActionVerb() {
            return actionVerb;
        }
    }
}
