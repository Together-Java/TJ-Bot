package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.NoSuchElementException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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

    // %s is formatted to the action verb.
    private static final String LOG_EMBED_DESCRIPTION = "%s tag **%s**"; 
    
    private static final String CONTENT_FILE_NAME = "content.md";
    private static final String NEW_CONTENT_FILE_NAME = "new_content.md";
    private static final String PREVIOUS_CONTENT_FILE_NAME = "previous_content.md";

    private final TagSystem tagSystem;
    private final Predicate<String> hasRequiredRole;

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     * @param config the config to use for this
     */
    public TagManageCommand(TagSystem tagSystem, @NotNull Config config) {
        super("tag-manage", "Provides commands to manage all tags", SlashCommandVisibility.GUILD);

        this.tagSystem = tagSystem;
        hasRequiredRole = Pattern.compile(config.getTagManageRolePattern()).asMatchPredicate();

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
                            .addOption(OptionType.STRING, MESSAGE_ID_OPTION, MESSAGE_ID_DESCRIPTION,
                                    true),
                new SubcommandData(Subcommand.EDIT.name, "Edits a tag, the old content is replaced")
                    .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true)
                    .addOption(OptionType.STRING, CONTENT_OPTION, CONTENT_DESCRIPTION, true),
                new SubcommandData(Subcommand.EDIT_WITH_MESSAGE.name,
                        "Edits a tag, the old content is replaced. Content is retrieved from the given message.")
                            .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true)
                            .addOption(OptionType.STRING, MESSAGE_ID_OPTION, MESSAGE_ID_DESCRIPTION,
                                    true),
                new SubcommandData(Subcommand.DELETE.name, "Deletes a tag")
                    .addOption(OptionType.STRING, ID_OPTION, ID_DESCRIPTION, true));
    }

    private static void sendSuccessMessage(@NotNull Interaction event, @NotNull String id,
            @NotNull String actionVerb) {
        logger.info("User '{}' {} the tag with id '{}'.", event.getUser().getId(), actionVerb, id);

        event
            .replyEmbeds(new EmbedBuilder().setTitle("Success")
                .setDescription("Successfully %s tag '%s'.".formatted(actionVerb, id))
                .setFooter(event.getUser().getName())
                .setTimestamp(Instant.now())
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
    private static OptionalLong parseMessageIdAndHandle(@NotNull String messageId,
            @NotNull Interaction event) {
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
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        if (!hasTagManageRole(Objects.requireNonNull(event.getMember()))) {
            event.reply("Tags can only be managed by users with a corresponding role.")
                .setEphemeral(true)
                .queue();
            return;
        }

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

    private void rawTag(@NotNull SlashCommandEvent event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        if (tagSystem.handleIsUnknownTag(id, event)) {
            return;
        }

        String content = tagSystem.getTag(id).orElseThrow();
        event.reply("")
            .addFile(content.getBytes(StandardCharsets.UTF_8), CONTENT_FILE_NAME)
            .queue();
    }

    private void createTag(@NotNull CommandInteraction event) {
        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        handleAction(TagStatus.NOT_EXISTS, id -> tagSystem.putTag(id, content), event,
                Subcommand.CREATE, content);
    }

    private void createTagWithMessage(@NotNull CommandInteraction event) {
        handleActionWithMessage(TagStatus.NOT_EXISTS, tagSystem::putTag, event,
                Subcommand.CREATE_WITH_MESSAGE);
    }

    private void editTag(@NotNull CommandInteraction event) {
        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        handleAction(TagStatus.EXISTS, id -> tagSystem.putTag(id, content), event, Subcommand.EDIT,
                content);
    }

    private void editTagWithMessage(@NotNull CommandInteraction event) {
        handleActionWithMessage(TagStatus.EXISTS, tagSystem::putTag, event,
                Subcommand.EDIT_WITH_MESSAGE);
    }

    private void deleteTag(@NotNull CommandInteraction event) {
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
    private void handleAction(@NotNull TagStatus requiredTagStatus,
            @NotNull Consumer<? super String> idAction, @NotNull CommandInteraction event,
            @NotNull Subcommand subcommand, @Nullable String newContent) {

        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        if (isWrongTagStatusAndHandle(requiredTagStatus, id, event)) {
            return;
        }

        String previousContent = getTagPreviousContent(subcommand, id);

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
    private void handleActionWithMessage(@NotNull TagStatus requiredTagStatus,
            @NotNull BiConsumer<? super String, ? super String> idAndContentAction,
            @NotNull CommandInteraction event, @NotNull Subcommand subcommand) {

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
            String previousContent = getTagPreviousContent(subcommand, tagId);

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
     * Gets the previous content of a tag, or {@code "Unable to retrieve previous content"} if was
     * unable to.
     * 
     * @param subcommand the subcommand to be executed
     * @param id the id of the tag to get its previous content
     * @return the previous content of the tag, or {@code "Unable to retrieve previous content"} if
     *         was unable to
     */
    private String getTagPreviousContent(Subcommand subcommand, String id) {
        if (EnumSet.of(Subcommand.DELETE, Subcommand.EDIT, Subcommand.EDIT_WITH_MESSAGE)
            .contains(subcommand)) {
            try {
                return tagSystem.getTag(id).orElseThrow();
            } catch (NoSuchElementException e) {
                // NOTE Rare race condition, for example if another thread deleted the tag in the
                // meantime
                logger.warn(String.format(
                        "tried to retrieve previous content of tag '%s', but the content doesn't exist.",
                        id));
            }
        }
        return "Unable to retrieve previous content";
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
    private boolean isWrongTagStatusAndHandle(@NotNull TagStatus requiredTagStatus,
            @NotNull String id, @NotNull Interaction event) {
        if (requiredTagStatus == TagStatus.EXISTS) {
            return tagSystem.handleIsUnknownTag(id, event);
        } else if (requiredTagStatus == TagStatus.NOT_EXISTS) {
            if (tagSystem.hasTag(id)) {
                event.reply("The tag with id '%s' already exists.".formatted(id))
                    .setEphemeral(true)
                    .queue();
                return true;
            }
        } else {
            throw new AssertionError("Unknown tag status '%s'".formatted(requiredTagStatus));
        }
        return false;
    }

    private void logAction(@NotNull Subcommand subcommand, @NotNull Guild guild,
            @NotNull User author, @NotNull TemporalAccessor timestamp, @NotNull String id,
            @Nullable String newContent, @Nullable String previousContent) {

        if (EnumSet
            .of(Subcommand.CREATE, Subcommand.CREATE_WITH_MESSAGE, Subcommand.EDIT,
                    Subcommand.EDIT_WITH_MESSAGE)
            .contains(subcommand) && newContent == null) {
            throw new IllegalArgumentException(
                    "newContent is null even though the subcommand should supply a value.");
        }

        if (EnumSet.of(Subcommand.EDIT, Subcommand.EDIT_WITH_MESSAGE, Subcommand.DELETE)
            .contains(subcommand) && previousContent == null) {
            throw new IllegalArgumentException(
                    "previousContent is null even though the subcommand should supply a value.");
        }

        switch (subcommand) {
            case CREATE -> ModAuditLogWriter.write("Tag-Manage Create",
                    String.format(LOG_EMBED_DESCRIPTION, subcommand.getActionVerb(), id), author,
                    timestamp, guild, new ModAuditLogWriter.Attachment(CONTENT_FILE_NAME,
                            Objects.requireNonNull(newContent)));

            case CREATE_WITH_MESSAGE -> ModAuditLogWriter.write("Tag-Manage Create with message",
                    String.format(LOG_EMBED_DESCRIPTION, subcommand.getActionVerb(), id), author,
                    timestamp, guild, new ModAuditLogWriter.Attachment(CONTENT_FILE_NAME,
                            Objects.requireNonNull(newContent)));

            case EDIT -> ModAuditLogWriter.write("Tag-Manage Edit",
                    String.format(LOG_EMBED_DESCRIPTION, subcommand.getActionVerb(), id), author,
                    timestamp, guild,
                    new ModAuditLogWriter.Attachment(NEW_CONTENT_FILE_NAME,
                            Objects.requireNonNull(newContent)),
                    new ModAuditLogWriter.Attachment(PREVIOUS_CONTENT_FILE_NAME,
                            Objects.requireNonNull(previousContent)));

            case EDIT_WITH_MESSAGE -> ModAuditLogWriter.write("Tag-Manage Edit with message",
                    String.format(LOG_EMBED_DESCRIPTION, subcommand.getActionVerb(), id), author,
                    timestamp, guild,
                    new ModAuditLogWriter.Attachment(NEW_CONTENT_FILE_NAME,
                            Objects.requireNonNull(newContent)),
                    new ModAuditLogWriter.Attachment(PREVIOUS_CONTENT_FILE_NAME,
                            Objects.requireNonNull(previousContent)));

            case DELETE -> ModAuditLogWriter.write("Tag-Manage Delete",
                    String.format(LOG_EMBED_DESCRIPTION, subcommand.getActionVerb(), id), author,
                    timestamp, guild, new ModAuditLogWriter.Attachment(PREVIOUS_CONTENT_FILE_NAME,
                            Objects.requireNonNull(previousContent)));

            default -> throw new IllegalArgumentException(String.format(
                    "The subcommand '%s' is not intended to be logged to the mod audit channel.",
                    subcommand.name()));
        }
    }

    private boolean hasTagManageRole(@NotNull Member member) {
        return member.getRoles().stream().map(Role::getName).anyMatch(hasRequiredRole);
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

        private final String name;
        private final String actionVerb;

        Subcommand(@NotNull String name, @NotNull String actionVerb) {
            this.name = name;
            this.actionVerb = actionVerb;
        }

        @NotNull
        String getName() {
            return name;
        }

        static Subcommand fromName(@NotNull String name) {
            for (Subcommand subcommand : Subcommand.values()) {
                if (subcommand.name.equals(name)) {
                    return subcommand;
                }
            }
            throw new IllegalArgumentException(
                    "Subcommand with name '%s' is unknown".formatted(name));
        }

        @NotNull
        String getActionVerb() {
            return this.actionVerb;
        }
    }
}
