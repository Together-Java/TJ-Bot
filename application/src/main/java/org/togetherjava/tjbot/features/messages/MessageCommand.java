package org.togetherjava.tjbot.features.messages;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
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

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Implements the {@code /message} command, which offers utility dealing with messages. Available
 * subcommands are:
 * <ul>
 * <li>{@code raw}</li>
 * <li>{@code post}</li>
 * <li>{@code post-with-message}</li>
 * <li>{@code edit}</li>
 * <li>{@code edit-with-message}</li>
 * </ul>
 */
public final class MessageCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MessageCommand.class);
    static final String CONTENT_MESSAGE_ID_OPTION = "content-message-id";
    private static final String CONTENT_MESSAGE_ID_DESCRIPTION =
            "the id of the message to read content from, must be in the channel this command is invoked";
    static final String SRC_CHANNEL_OPTION = "source";
    private static final String EDIT_SRC_CHANNEL_DESCRIPTION = "where to find the message to edit";
    static final String DEST_CHANNEL_OPTION = "destination";
    private static final String DEST_CHANNEL_DESCRIPTION = "where to post the message";
    static final String CONTENT_OPTION = "content";
    private static final String CONTENT_DESCRIPTION = "the content of the message";
    static final String EDIT_MESSAGE_ID_OPTION = "edit-message-id";
    private static final String EDIT_MESSAGE_ID_DESCRIPTION = "the id of the message to edit";

    private static final Color AMBIENT_COLOR = new Color(24, 109, 221, 255);

    private static final String CONTENT_FILE_NAME = "content.md";

    /**
     * Creates a new instance.
     */
    public MessageCommand() {
        super("message", "Provides commands to work with messages", CommandVisibility.GUILD);

        SubcommandData raw = new SubcommandData(Subcommand.RAW.name,
                "View the raw content of a message, without Discord interpreting any of its content")
            .addOption(OptionType.CHANNEL, SRC_CHANNEL_OPTION,
                    "where to find the message to retrieve content from", true)
            .addOption(OptionType.STRING, CONTENT_MESSAGE_ID_OPTION,
                    "the id of the message to read content from", true);

        SubcommandData post =
                new SubcommandData(Subcommand.POST.name, "Let this bot post a message")
                    .addOption(OptionType.CHANNEL, DEST_CHANNEL_OPTION, DEST_CHANNEL_DESCRIPTION,
                            true)
                    .addOption(OptionType.STRING, CONTENT_OPTION, CONTENT_DESCRIPTION, true);
        SubcommandData postWithMessage = new SubcommandData(Subcommand.POST_WITH_MESSAGE.name,
                "Let this bot post a message. Content is retrieved from the given message.")
            .addOption(OptionType.CHANNEL, DEST_CHANNEL_OPTION, DEST_CHANNEL_DESCRIPTION, true)
            .addOption(OptionType.STRING, CONTENT_MESSAGE_ID_OPTION, CONTENT_MESSAGE_ID_DESCRIPTION,
                    true);

        SubcommandData edit = new SubcommandData(Subcommand.EDIT.name,
                "Edits a message posted by this bot, the old content is replaced")
            .addOption(OptionType.CHANNEL, SRC_CHANNEL_OPTION, EDIT_SRC_CHANNEL_DESCRIPTION, true)
            .addOption(OptionType.STRING, EDIT_MESSAGE_ID_OPTION, EDIT_MESSAGE_ID_DESCRIPTION, true)
            .addOption(OptionType.STRING, CONTENT_OPTION, CONTENT_DESCRIPTION, true);
        SubcommandData editWithMessage = new SubcommandData(Subcommand.EDIT_WITH_MESSAGE.name,
                "Edits a message posted by this bot. Content is retrieved from the given message.")
            .addOption(OptionType.CHANNEL, SRC_CHANNEL_OPTION, EDIT_SRC_CHANNEL_DESCRIPTION, true)
            .addOption(OptionType.STRING, EDIT_MESSAGE_ID_OPTION, EDIT_MESSAGE_ID_DESCRIPTION, true)
            .addOption(OptionType.STRING, CONTENT_MESSAGE_ID_OPTION, CONTENT_MESSAGE_ID_DESCRIPTION,
                    true);

        getData().addSubcommands(raw, post, postWithMessage, edit, editWithMessage);
    }

    /**
     * Attempts to convert the given channel into a {@link TextChannel}.
     * <p>
     * If the channel is not a text channel, an error message is send to the user.
     *
     * @param channel the channel to convert
     * @param event the event to send messages with
     * @return the channel as text channel, if successful
     */
    private static Optional<TextChannel> handleExpectMessageChannel(GuildChannelUnion channel,
            IReplyCallback event) {
        if (channel.getType() != ChannelType.TEXT) {
            event
                .reply("The given channel ('%s') is not a text-channel."
                    .formatted(channel.getName()))
                .setEphemeral(true)
                .queue();
            return Optional.empty();
        }
        return Optional.of(channel.asTextChannel());
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
        } catch (NumberFormatException _) {
            event
                .reply("The given message id '%s' is invalid, expected a number."
                    .formatted(messageId))
                .setEphemeral(true)
                .queue();
            return OptionalLong.empty();
        }
    }

    private static void handleMessageRetrieveFailed(Throwable failure, IDeferrableCallback event,
            long messageId) {
        handleMessageRetrieveFailed(failure, event, List.of(messageId));
    }

    private static void handleMessageRetrieveFailed(Throwable failure, IDeferrableCallback event,
            List<Long> messageIds) {
        if (failure instanceof ErrorResponseException ex
                && ex.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
            event.getHook()
                .editOriginal("The messages with ids '%s' do not exist.".formatted(messageIds))
                .queue();
            return;
        }

        logger.warn("Unable to retrieve the messages with ids '{}' for an unknown reason.",
                messageIds, failure);
        event.getHook()
            .editOriginal(
                    "Something unexpected went wrong trying to locate the messages with ids '%s'."
                        .formatted(messageIds))
            .queue();
    }

    private static boolean handleIsMessageFromOtherUser(Message message,
            IDeferrableCallback event) {
        if (message.getAuthor().equals(message.getJDA().getSelfUser())) {
            return false;
        }
        event.getHook()
            .editOriginal(
                    "The message to edit must be from this bot but was posted by another user.")
            .queue();
        return true;
    }

    private static void sendSuccessMessage(IDeferrableCallback event, Subcommand action) {
        event.getHook()
            .editOriginalEmbeds(new EmbedBuilder().setTitle("Success")
                .setDescription("Successfully %s message.".formatted(action.getActionVerbPast()))
                .setColor(MessageCommand.AMBIENT_COLOR)
                .build())
            .queue();
    }

    private static void handleActionFailed(Throwable failure, IDeferrableCallback event,
            Subcommand action) {
        String verb = action.getActionVerb();
        logger.warn("Unable to {} message for an unknown reason.", verb, failure);
        event.getHook()
            .editOriginal(
                    "Something unexpected went wrong trying to '%s' the message.".formatted(verb))
            .queue();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (Subcommand.fromName(event.getSubcommandName())) {
            case RAW -> rawMessage(event);
            case POST -> postMessage(event);
            case POST_WITH_MESSAGE -> postMessageUsingMessageContent(event);
            case EDIT -> editMessage(event);
            case EDIT_WITH_MESSAGE -> editMessageUsingMessageContent(event);
            default -> throw new AssertionError(
                    "Unexpected subcommand '%s'".formatted(event.getSubcommandName()));
        }
    }

    private void rawMessage(SlashCommandInteractionEvent event) {
        Optional<TextChannel> srcChannelOpt = handleExpectMessageChannel(
                Objects.requireNonNull(event.getOption(SRC_CHANNEL_OPTION)).getAsChannel(), event);
        if (srcChannelOpt.isEmpty()) {
            return;
        }
        TextChannel srcChannel = srcChannelOpt.orElseThrow();

        OptionalLong contentMessageIdOpt = parseMessageIdAndHandle(
                Objects.requireNonNull(event.getOption(CONTENT_MESSAGE_ID_OPTION)).getAsString(),
                event);
        if (contentMessageIdOpt.isEmpty()) {
            return;
        }
        long contentMessageId = contentMessageIdOpt.orElseThrow();

        event.deferReply().queue();
        srcChannel.retrieveMessageById(contentMessageId).queue(contentMessage -> {
            String content = contentMessage.getContentRaw();
            event.getHook()
                .editOriginal("")
                .setFiles(FileUpload.fromData(content.getBytes(StandardCharsets.UTF_8),
                        CONTENT_FILE_NAME))
                .queue();
        }, failure -> handleMessageRetrieveFailed(failure, event, contentMessageId));
    }

    private void postMessage(CommandInteraction event) {
        Subcommand action = Subcommand.POST;
        Optional<TextChannel> destChannelOpt = handleExpectMessageChannel(
                Objects.requireNonNull(event.getOption(DEST_CHANNEL_OPTION)).getAsChannel(), event);
        if (destChannelOpt.isEmpty()) {
            return;
        }
        TextChannel destChannel = destChannelOpt.orElseThrow();

        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        event.deferReply().queue();
        destChannel.sendMessage(content)
            .queue(_ -> sendSuccessMessage(event, action),
                    failure -> handleActionFailed(failure, event, action));
    }

    private void postMessageUsingMessageContent(CommandInteraction event) {
        Subcommand action = Subcommand.POST_WITH_MESSAGE;
        Optional<TextChannel> destChannelOpt = handleExpectMessageChannel(
                Objects.requireNonNull(event.getOption(DEST_CHANNEL_OPTION)).getAsChannel(), event);
        if (destChannelOpt.isEmpty()) {
            return;
        }
        TextChannel destChannel = destChannelOpt.orElseThrow();

        OptionalLong contentMessageIdOpt = parseMessageIdAndHandle(
                Objects.requireNonNull(event.getOption(CONTENT_MESSAGE_ID_OPTION)).getAsString(),
                event);
        if (contentMessageIdOpt.isEmpty()) {
            return;
        }
        long contentMessageId = contentMessageIdOpt.orElseThrow();

        event.deferReply().queue();
        event.getMessageChannel().retrieveMessageById(contentMessageId).queue(contentMessage -> {
            String content = contentMessage.getContentRaw();
            destChannel.sendMessage(content)
                .queue(_ -> sendSuccessMessage(event, action),
                        failure -> handleActionFailed(failure, event, action));
        }, failure -> handleMessageRetrieveFailed(failure, event, contentMessageId));
    }

    private void editMessage(CommandInteraction event) {
        Subcommand action = Subcommand.EDIT;
        Optional<TextChannel> srcChannelOpt = handleExpectMessageChannel(
                Objects.requireNonNull(event.getOption(SRC_CHANNEL_OPTION)).getAsChannel(), event);
        if (srcChannelOpt.isEmpty()) {
            return;
        }
        TextChannel srcChannel = srcChannelOpt.orElseThrow();

        OptionalLong editingMessageIdOpt = parseMessageIdAndHandle(
                Objects.requireNonNull(event.getOption(EDIT_MESSAGE_ID_OPTION)).getAsString(),
                event);
        if (editingMessageIdOpt.isEmpty()) {
            return;
        }
        long editingMessageId = editingMessageIdOpt.orElseThrow();
        String content = Objects.requireNonNull(event.getOption(CONTENT_OPTION)).getAsString();

        event.deferReply().queue();
        srcChannel.retrieveMessageById(editingMessageId).queue(editingMessage -> {
            if (handleIsMessageFromOtherUser(editingMessage, event)) {
                return;
            }
            editingMessage.editMessage(content)
                .queue(_ -> sendSuccessMessage(event, action),
                        failure -> handleActionFailed(failure, event, action));
        }, failure -> handleMessageRetrieveFailed(failure, event, editingMessageId));
    }

    private void editMessageUsingMessageContent(CommandInteraction event) {
        Subcommand action = Subcommand.EDIT_WITH_MESSAGE;
        Optional<TextChannel> srcChannelOpt = handleExpectMessageChannel(
                Objects.requireNonNull(event.getOption(SRC_CHANNEL_OPTION)).getAsChannel(), event);
        if (srcChannelOpt.isEmpty()) {
            return;
        }
        TextChannel srcChannel = srcChannelOpt.orElseThrow();

        OptionalLong editingMessageIdOpt = parseMessageIdAndHandle(
                Objects.requireNonNull(event.getOption(EDIT_MESSAGE_ID_OPTION)).getAsString(),
                event);
        if (editingMessageIdOpt.isEmpty()) {
            return;
        }
        long editingMessageId = editingMessageIdOpt.orElseThrow();

        OptionalLong contentMessageIdOpt = parseMessageIdAndHandle(
                Objects.requireNonNull(event.getOption(CONTENT_MESSAGE_ID_OPTION)).getAsString(),
                event);
        if (contentMessageIdOpt.isEmpty()) {
            return;
        }
        long contentMessageId = contentMessageIdOpt.orElseThrow();

        event.deferReply().queue();
        record Messages(Message editingMessage, Message contentMessage) {
        }
        srcChannel.retrieveMessageById(editingMessageId)
            .and(event.getMessageChannel().retrieveMessageById(contentMessageId), Messages::new)
            .queue(messages -> {
                if (handleIsMessageFromOtherUser(messages.editingMessage, event)) {
                    return;
                }

                String content = messages.contentMessage.getContentRaw();
                messages.editingMessage.editMessage(content)
                    .queue(_ -> sendSuccessMessage(event, action),
                            failure -> handleActionFailed(failure, event, action));
            }, failure -> handleMessageRetrieveFailed(failure, event,
                    List.of(editingMessageId, contentMessageId)));
    }

    enum Subcommand {
        RAW("raw", "", ""),
        POST("post", "post", "posted"),
        POST_WITH_MESSAGE("post-with-message", "post", "posted"),
        EDIT("edit", "edit", "edited"),
        EDIT_WITH_MESSAGE("edit-with-message", "edit", "edited");

        private final String name;
        private final String actionVerb;
        private final String actionVerbPast;

        Subcommand(String name, String actionVerb, String actionVerbPast) {
            this.name = name;
            this.actionVerb = actionVerb;
            this.actionVerbPast = actionVerbPast;
        }

        String getName() {
            return name;
        }

        String getActionVerb() {
            return actionVerb;
        }

        String getActionVerbPast() {
            return actionVerbPast;
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
