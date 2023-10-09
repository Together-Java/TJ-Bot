package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumPost;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class TransferQuestionCommand extends BotCommandAdapter
        implements MessageContextCommand {
    private static final Logger logger = LoggerFactory.getLogger(TransferQuestionCommand.class);
    private static final String COMMAND_NAME = "transfer-question";
    private static final String TRANSFER_QUESTION_TITLE_ID = "transferID";
    private static final String TRANSFER_QUESTION_INPUT_ID = "transferQuestion";
    private static final String TRANSFER_QUESTION_TAG = "tags";
    private static final int TITLE_MAX_LENGTH = 50;
    private static final Pattern TITLE_COMPACT_REMOVAL_PATTERN = Pattern.compile("\\W");
    private static final int TITLE_COMPACT_LENGTH_MIN = 2;
    private static final int TITLE_COMPACT_LENGTH_MAX = 30;
    private final Predicate<String> isHelpForumName;
    private final List<String> defaultTags;


    /**
     * Creates a new instance.
     */
    public TransferQuestionCommand(Config config) {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);

        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();

        defaultTags = config.getHelpSystem().getCategories();
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        String originalMessage = event.getTarget().getContentRaw();
        String originalMessageId = event.getTarget().getId();
        String originalChannelId = event.getChannel().getId();
        String authorId = event.getTarget().getAuthor().getId();

        TextInput transferQuestionTitle =
                TextInput.create(TRANSFER_QUESTION_TITLE_ID, "Title", TextInputStyle.SHORT)
                    .setMaxLength(70)
                    .setMinLength(8)
                    .setValue(createTitle(originalMessage))
                    .build();

        TextInput transferQuestionInput = TextInput
            .create(TRANSFER_QUESTION_INPUT_ID, "Transfer question menu", TextInputStyle.PARAGRAPH)
            .setValue(originalMessage)
            .setRequiredRange(3, 2000)
            .build();

        TextInput transferQuestionTag = TextInput
            .create(TRANSFER_QUESTION_TAG, "Transfer question tags", TextInputStyle.SHORT)
            .setValue("Java")
            .build();

        String transferQuestionModalComponentID =
                generateComponentId(authorId, originalMessageId, originalChannelId);
        Modal transferModal =
                Modal.create(transferQuestionModalComponentID, "transfer question menu")
                    .addActionRow(transferQuestionTitle)
                    .addActionRow(transferQuestionInput)
                    .addActionRow(transferQuestionTag)
                    .build();

        event.replyModal(transferModal)
            .queue(success -> logger.info(
                    "{} with id: {}  triggered the transfer action on message with id: {}",
                    event.getUser().getName(), event.getUser().getId(), originalMessageId),
                    failed -> {
                    });
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        event.deferReply().queue();

        event.getJDA()
            .retrieveUserById(args.get(0))
            .flatMap(fetchedUser -> createForumPost(event, fetchedUser))
            .flatMap(user -> dmUser(event.getChannel(), user, event.getGuild()))
            .flatMap(dmSent -> deleteOriginalMessage(event.getJDA(), args.get(2), args.get(1)))
            .flatMap(deletedOriginalMessage -> event.getHook()
                .sendMessage(String.format("Question transferred to %s",
                        getHelperForum(event.getJDA()))))
            .queue();
    }

    private static String createTitle(String message) {
        if (message.length() >= TITLE_MAX_LENGTH) {
            int lastWordEnd = message.lastIndexOf(' ', TITLE_MAX_LENGTH);

            if (lastWordEnd == -1) {
                lastWordEnd = TITLE_MAX_LENGTH;
            }

            message = message.substring(0, lastWordEnd);
        }

        return isTitleValid(message) ? message : "Untitled";
    }

    private static boolean isTitleValid(CharSequence title) {
        String titleCompact = TITLE_COMPACT_REMOVAL_PATTERN.matcher(title).replaceAll("");

        return titleCompact.length() >= TITLE_COMPACT_LENGTH_MIN
                && titleCompact.length() <= TITLE_COMPACT_LENGTH_MAX;
    }

    private RestAction<User> createForumPost(ModalInteractionEvent event, User originalUser) {

        String originalMessage = event.getValue(TRANSFER_QUESTION_INPUT_ID).getAsString();

        MessageEmbed embedForPost = makeEmbedForPost(originalUser, originalMessage);

        MessageCreateData forumMessage = MessageCreateData.fromEmbeds(embedForPost);
        String forumTitle = event.getValue(TRANSFER_QUESTION_TITLE_ID).getAsString();
        String transferQuestionTag = event.getValue(TRANSFER_QUESTION_TAG).getAsString();

        ForumChannel questionsForum = getHelperForum(event.getJDA());

        String queryTag = StringDistances.closestMatch(transferQuestionTag, defaultTags)
            .orElse(defaultTags.get(0));

        ForumTag defaultTag = getDefaultTagOr(questionsForum.getAvailableTagsByName(queryTag, true),
                () -> questionsForum.getAvailableTagsByName(defaultTags.get(0), true).get(0));

        return questionsForum.createForumPost(forumTitle, forumMessage)
            .setTags(ForumTagSnowflake.fromId(defaultTag.getId()))
            .map(ForumPost::getMessage)
            .flatMap(message -> message.reply(originalUser.getAsMention()))
            .map(sent -> originalUser);
    }

    private RestAction<Message> dmUser(MessageChannelUnion sourceChannel, User originalUser,
            Guild guild) {

        return originalUser.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hello 👋 You have asked a question on %s in the wrong channel. Not a big deal, but none of the experts who could help you are reading your question there 🙁.

                            Your question has been automatically transferred to %s, please continue there. You might also want to give #welcome a quick read, thank you 👍.
                            """
                        .formatted(guild.getName(), getHelperForum(guild.getJDA()))))
            .onErrorFlatMap(error -> sourceChannel.sendMessage(
                    """
                            Hello %s 👋 You have asked a question in the wrong channel. Not a big deal, but none of the experts who could help you are reading your question there 🙁.

                            Your question has been automatically transferred to %s, please continue there. You might also want to give #welcome a quick read, thank you 👍.
                            """
                        .formatted(originalUser.getAsMention(), getHelperForum(guild.getJDA()))));
    }

    private RestAction<Void> deleteOriginalMessage(JDA jda, String channelId, String messageId) {
        return jda.getTextChannelById(channelId).deleteMessageById(messageId);
    }

    private ForumChannel getHelperForum(JDA jda) {
        Optional<ForumChannel> forumChannelOptional = jda.getForumChannels()
            .stream()
            .filter(forumChannel -> isHelpForumName.test(forumChannel.getName()))
            .findFirst();

        return forumChannelOptional
            .orElseThrow(() -> new RuntimeException("Helper Forum Not found"));
    }

    private static ForumTag getDefaultTagOr(List<ForumTag> tagsFoundOnForum,
            Supplier<ForumTag> defaultTag) {
        return tagsFoundOnForum.isEmpty() ? defaultTag.get() : tagsFoundOnForum.get(0);
    }

    private MessageEmbed makeEmbedForPost(User originalUser, String originalMessage) {
        return new EmbedBuilder()
            .setAuthor(originalUser.getName(), originalUser.getAvatarUrl(),
                    originalUser.getAvatar().getUrl())
            .setDescription(originalMessage)
            .setColor(new Color(50, 164, 168))
            .build();
    }
}
