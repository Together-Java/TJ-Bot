package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
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
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
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
    private static final String MODAL_TITLE_ID = "transferID";
    private static final String MODAL_INPUT_ID = "transferQuestion";
    private static final String MODAL_TAG = "tags";
    private static final int TITLE_GUESS_MAX_LENGTH = 50;
    private static final Pattern TITLE_GUESS_COMPACT_REMOVAL_PATTERN = Pattern.compile("\\W");
    private static final int TITLE_GUESS_COMPACT_LENGTH_MIN = 2;
    private static final int TITLE_GUESS_COMPACT_LENGTH_MAX = 30;
    private static final Color EMBED_COLOR = new Color(50, 164, 168);
    private final Predicate<String> isHelpForumName;
    private final List<String> defaultTags;


    /**
     * Creates a new instance.
     *
     * @param config to get the helper forum and tags
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
        String mostCommonTag = defaultTags.get(0);

        TextInput modalTitle = TextInput.create(MODAL_TITLE_ID, "Title", TextInputStyle.SHORT)
            .setMaxLength(70)
            .setMinLength(4)
            .setPlaceholder("Describe the question in short")
            .setValue(createTitle(originalMessage))
            .build();

        TextInput modalInput =
                TextInput.create(MODAL_INPUT_ID, "Question", TextInputStyle.PARAGRAPH)
                    .setValue(originalMessage)
                    .setRequiredRange(3, 2000)
                    .setPlaceholder("Contents of the question")
                    .build();

        TextInput modalTag = TextInput.create(MODAL_TAG, "Most fitting tag", TextInputStyle.SHORT)
            .setValue(mostCommonTag)
            .setPlaceholder("Suitable tag for the question")
            .build();

        String modalComponentId =
                generateComponentId(authorId, originalMessageId, originalChannelId);
        Modal transferModal = Modal.create(modalComponentId, "Transfer this question")
            .addActionRow(modalTitle)
            .addActionRow(modalInput)
            .addActionRow(modalTag)
            .build();

        event.replyModal(transferModal)
            .queue(success -> logger.debug(
                    "{} with id: {}  triggered the transfer action on forumPost with id: {}",
                    event.getUser().getName(), event.getUser().getId(), originalMessageId));
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        event.deferEdit().queue();

        String authorId = args.get(0);
        String messageId = args.get(1);
        String channelId = args.get(2);

        event.getJDA()
            .retrieveUserById(authorId)
            .flatMap(fetchedUser -> createForumPost(event, fetchedUser))
            .flatMap(createdforumPost -> dmUser(event.getChannel(), createdforumPost,
                    event.getGuild()))
            .flatMap(dmSent -> deleteOriginalMessage(event.getJDA(), channelId, messageId))
            .queue();
    }

    private static String createTitle(String message) {
        if (message.length() >= TITLE_GUESS_MAX_LENGTH) {
            int lastWordEnd = message.lastIndexOf(' ', TITLE_GUESS_MAX_LENGTH);

            if (lastWordEnd == -1) {
                lastWordEnd = TITLE_GUESS_MAX_LENGTH;
            }

            message = message.substring(0, lastWordEnd);
        }

        return isTitleValid(message) ? message : "Untitled";
    }

    private static boolean isTitleValid(CharSequence title) {
        String titleCompact = TITLE_GUESS_COMPACT_REMOVAL_PATTERN.matcher(title).replaceAll("");

        return titleCompact.length() >= TITLE_GUESS_COMPACT_LENGTH_MIN
                && titleCompact.length() <= TITLE_GUESS_COMPACT_LENGTH_MAX;
    }

    private RestAction<ForumPost> createForumPost(ModalInteractionEvent event, User originalUser) {

        String originalMessage = event.getValue(MODAL_INPUT_ID).getAsString();

        MessageEmbed embedForPost = makeEmbedForPost(originalUser, originalMessage);

        MessageCreateData forumMessage = new MessageCreateBuilder()
            .addContent("%s has a question:".formatted(originalUser.getAsMention()))
            .setEmbeds(embedForPost)
            .build();

        String forumTitle = event.getValue(MODAL_TITLE_ID).getAsString();
        String transferQuestionTag = event.getValue(MODAL_TAG).getAsString();

        ForumChannel questionsForum = getHelperForum(event.getJDA());
        String mostCommonTag = defaultTags.get(0);

        String queryTag = StringDistances.closestMatch(transferQuestionTag, defaultTags)
            .orElse(mostCommonTag);

        ForumTag defaultTag = getDefaultTagOr(questionsForum.getAvailableTagsByName(queryTag, true),
                () -> questionsForum.getAvailableTagsByName(mostCommonTag, true).get(0));

        return questionsForum.createForumPost(forumTitle, forumMessage)
            .setTags(ForumTagSnowflake.fromId(defaultTag.getId()))
            .map(createdPost -> new ForumPost(originalUser, createdPost.getMessage()));
    }

    private RestAction<Message> dmUser(MessageChannelUnion sourceChannel, ForumPost forumPost,
            Guild guild) {

        String messageTemplate =
                """
                        Hello%s 👋 You have asked a question in the wrong channel%s. Not a big deal, but none of the experts who could help you are reading your question there 🙁

                        Your question has been automatically transferred to %s, please continue there, thank you 👍
                        """;

        String messageForDm = messageTemplate.formatted("", " on" + " " + guild.getName(),
                forumPost.message.getJumpUrl());

        String messageOnDmFailure = messageTemplate.formatted(" " + forumPost.author.getAsMention(),
                "", forumPost.message.getJumpUrl());

        return forumPost.author.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(messageForDm))
            .onErrorFlatMap(error -> sourceChannel.sendMessage(messageOnDmFailure));
    }

    private RestAction<Void> deleteOriginalMessage(JDA jda, String channelId, String messageId) {
        return jda.getTextChannelById(channelId).deleteMessageById(messageId);
    }

    private ForumChannel getHelperForum(JDA jda) {
        Optional<ForumChannel> forumChannelOptional = jda.getForumChannels()
            .stream()
            .filter(forumChannel -> isHelpForumName.test(forumChannel.getName()))
            .findFirst();

        return forumChannelOptional.orElseThrow(() -> new IllegalStateException(
                "Did not find the helper-forum while trying to transfer a question. Make sure the config is setup properly."));
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
            .setColor(EMBED_COLOR)
            .build();
    }

    private record ForumPost(User author, Message message) {
    }
}
