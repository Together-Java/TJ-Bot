package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.utils.MessageUtils;

import java.util.Objects;
import java.util.OptionalLong;
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
    private static final String ID_OPTION = "id";
    private static final String ID_DESCRIPTION = "the id of the tag";
    private static final String CONTENT_OPTION = "content";
    private static final String CONTENT_DESCRIPTION = "the content of the tag";
    private static final String MESSAGE_ID_OPTION = "message-id";
    private static final String MESSAGE_ID_DESCRIPTION = "the id of the message to refer to";
    private final TagSystem tagSystem;

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     */
    public TagManageCommand(TagSystem tagSystem) {
        super("tag-manage", "Provides commands to manage all tags", SlashCommandVisibility.GUILD);

        this.tagSystem = tagSystem;

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
        event.replyEmbeds(MessageUtils.generateEmbed("Success",
                "Successfully %s tag '%s'.".formatted(actionVerb, id), event.getUser(),
                TagSystem.AMBIENT_COLOR))
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
        Member member = Objects.requireNonNull(event.getMember());

        if (!member.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply(
                    "Tags can only be managed by users who have the 'MESSAGE_MANAGE' permission.")
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
        if (tagSystem.isUnknownTagAndHandle(id, event)) {
            return;
        }

        event.replyEmbeds(MessageUtils.generateEmbed(null,
                MessageUtils.escapeDiscordMessage(tagSystem.getTag(id).orElseThrow()),
                event.getUser(), TagSystem.AMBIENT_COLOR))
            .queue();
    }

    private void createTag(@NotNull CommandInteraction event) {
        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        handleAction(TagStatus.NOT_EXISTS, id -> tagSystem.putTag(id, content), "created", event);
    }

    private void createTagWithMessage(@NotNull CommandInteraction event) {
        handleActionWithMessage(TagStatus.NOT_EXISTS, tagSystem::putTag, "created", event);
    }

    private void editTag(@NotNull CommandInteraction event) {
        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        handleAction(TagStatus.EXISTS, id -> tagSystem.putTag(id, content), "edited", event);
    }

    private void editTagWithMessage(@NotNull CommandInteraction event) {
        handleActionWithMessage(TagStatus.EXISTS, tagSystem::putTag, "edited", event);
    }

    private void deleteTag(@NotNull CommandInteraction event) {
        handleAction(TagStatus.EXISTS, tagSystem::deleteTag, "deleted", event);
    }

    /**
     * Executes the given action on the tag id and sends a success message to the user.
     * <p>
     * If the tag status does not line up with the required status, an error message is send to the
     * user.
     *
     * @param requiredTagStatus the required status of the tag
     * @param idAction the action to perform on the id
     * @param actionVerb the verb describing the executed action, i.e. <i>edited</i> or
     *        <i>created</i>, will be displayed in the message send to the user
     * @param event the event to send messages with, it must have an {@code id} option set
     */
    private void handleAction(@NotNull TagStatus requiredTagStatus,
            @NotNull Consumer<? super String> idAction, @NotNull String actionVerb,
            @NotNull CommandInteraction event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        if (isWrongTagStatusAndHandle(requiredTagStatus, id, event)) {
            return;
        }

        idAction.accept(id);
        sendSuccessMessage(event, id, actionVerb);
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
     * @param actionVerb the verb describing the executed action, i.e. <i>edited</i> or
     *        <i>created</i>, will be displayed in the message send to the user
     * @param event the event to send messages with, it must have an {@code id} and
     *        {@code message-id} option set
     */
    private void handleActionWithMessage(@NotNull TagStatus requiredTagStatus,
            @NotNull BiConsumer<? super String, ? super String> idAndContentAction,
            @NotNull String actionVerb, @NotNull CommandInteraction event) {
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
            idAndContentAction.accept(tagId, message.getContentRaw());
            sendSuccessMessage(event, tagId, actionVerb);
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
            return tagSystem.isUnknownTagAndHandle(id, event);
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

    private enum TagStatus {
        EXISTS,
        NOT_EXISTS
    }


    private enum Subcommand {
        RAW("raw"),
        CREATE("create"),
        CREATE_WITH_MESSAGE("create-with-message"),
        EDIT("edit"),
        EDIT_WITH_MESSAGE("edit-with-message"),
        DELETE("delete");

        private final String name;

        Subcommand(String name) {
            this.name = name;
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
    }
}
