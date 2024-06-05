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
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInput.Builder;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * This command can be used to transfer questions asked in any channel to the helper forum. The user
 * is given the chance to edit details of the question and upon submitting, the original message
 * will be deleted and recreated in the helper forum. The original author is notified and redirected
 * to the new post.
 */
public final class TransferQuestionCommand extends BotCommandAdapter
        implements MessageContextCommand {
    private static final Logger logger = LoggerFactory.getLogger(TransferQuestionCommand.class);
    private static final String COMMAND_NAME = "transfer-question";
    private static final String MODAL_TITLE_ID = "transferID";
    private static final String MODAL_INPUT_ID = "transferQuestion";
    private static final String MODAL_TAG = "tags";
    private static final int TITLE_MAX_LENGTH = 50;
    private static final Pattern TITLE_GUESS_COMPACT_REMOVAL_PATTERN = Pattern.compile("\\W");
    private static final int TITLE_MIN_LENGTH = 3;
    private static final Color EMBED_COLOR = new Color(50, 164, 168);
    private static final int INPUT_MAX_LENGTH = Message.MAX_CONTENT_LENGTH;
    private static final int INPUT_MIN_LENGTH = 3;
    private final Predicate<String> isHelpForumName;
    private final List<String> tags;
    private final ChatGptService chatGptService;


    /**
     * Creates a new instance.
     *
     * @param config to get the helper forum and tags
     * @param chatGptService the service used to ask ChatGPT questions via the API.
     */
    public TransferQuestionCommand(Config config, ChatGptService chatGptService) {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);

        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();

        tags = config.getHelpSystem().getCategories();
        this.chatGptService = chatGptService;
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {

        if (isInvalidForTransfer(event)) {
            return;
        }

        String originalMessage = event.getTarget().getContentRaw();
        String originalMessageId = event.getTarget().getId();
        String originalChannelId = event.getTarget().getChannel().getId();
        String authorId = event.getTarget().getAuthor().getId();
        String mostCommonTag = tags.getFirst();
        String chatGptPrompt =
                "Summarize the following text into a concise title or heading not more than 4-5 words, remove quotations if any: %s"
                    .formatted(originalMessage);
        Optional<String> chatGptResponse = chatGptService.ask(chatGptPrompt, "");
        String title = chatGptResponse.orElse(createTitle(originalMessage));
        if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }

        TextInput modalTitle = TextInput.create(MODAL_TITLE_ID, "Title", TextInputStyle.SHORT)
            .setMaxLength(TITLE_MAX_LENGTH)
            .setMinLength(TITLE_MIN_LENGTH)
            .setPlaceholder("Describe the question in short")
            .setValue(title)
            .build();

        Builder modalInputBuilder =
                TextInput.create(MODAL_INPUT_ID, "Question", TextInputStyle.PARAGRAPH)
                    .setRequiredRange(INPUT_MIN_LENGTH, INPUT_MAX_LENGTH)
                    .setPlaceholder("Contents of the question");

        if (!isQuestionTooShort(originalMessage)) {
            String trimmedMessage = getMessageUptoMaxLimit(originalMessage);
            modalInputBuilder.setValue(trimmedMessage);
        }

        TextInput modalTag = TextInput.create(MODAL_TAG, "Most fitting tag", TextInputStyle.SHORT)
            .setValue(mostCommonTag)
            .setPlaceholder("Suitable tag for the question")
            .build();

        String modalComponentId =
                generateComponentId(authorId, originalMessageId, originalChannelId);
        Modal transferModal = Modal.create(modalComponentId, "Transfer this question")
            .addActionRow(modalTitle)
            .addActionRow(modalInputBuilder.build())
            .addActionRow(modalTag)
            .build();

        event.replyModal(transferModal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        event.deferReply(true).queue();

        String authorId = args.getFirst();
        String messageId = args.get(1);
        String channelId = args.get(2);
        ForumChannel helperForum = getHelperForum(event.getJDA());

        // Has been handled if original message was deleted by now.
        // Deleted messages cause retrieveMessageById to fail.
        Consumer<Message> notHandledAction =
                any -> transferFlow(event, channelId, authorId, messageId);

        Consumer<Throwable> handledAction = failure -> {
            if (failure instanceof ErrorResponseException errorResponseException
                    && errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                alreadyHandled(event, helperForum);
                return;
            }
            logger.warn("Unknown error occurred on modal submission during question transfer.",
                    failure);
        };

        event.getChannel().retrieveMessageById(messageId).queue(notHandledAction, handledAction);
    }

    private void transferFlow(ModalInteractionEvent event, String channelId, String authorId,
            String messageId) {
        Function<ForumPostData, WebhookMessageCreateAction<Message>> sendMessageToTransferrer =
                post -> event.getHook()
                    .sendMessage("Transferred to %s"
                        .formatted(post.forumPost.getThreadChannel().getAsMention()));

        event.getJDA()
            .retrieveUserById(authorId)
            .flatMap(fetchedUser -> createForumPost(event, fetchedUser))
            .flatMap(createdForumPost -> dmUser(event.getChannel(), createdForumPost,
                    event.getGuild())
                .and(sendMessageToTransferrer.apply(createdForumPost)))
            .flatMap(dmSent -> deleteOriginalMessage(event.getJDA(), channelId, messageId))
            .queue();
    }

    private void alreadyHandled(ModalInteractionEvent event, ForumChannel helperForum) {
        event.getHook()
            .sendMessage(
                    "It appears that someone else has already transferred this question. Kindly see %s for details."
                        .formatted(helperForum.getAsMention()))
            .queue();
    }

    private static String createTitle(String message) {
        if (message.length() >= TITLE_MAX_LENGTH) {
            int lastWordEnd = message.lastIndexOf(' ', TITLE_MAX_LENGTH);

            if (lastWordEnd == -1) {
                lastWordEnd = TITLE_MAX_LENGTH;
            }

            message = message.substring(0, lastWordEnd).replace('\n', ' ');
        }

        return isTitleValid(message) ? message : "Untitled";
    }

    private static boolean isTitleValid(CharSequence title) {
        String titleCompact = TITLE_GUESS_COMPACT_REMOVAL_PATTERN.matcher(title).replaceAll("");

        return titleCompact.length() >= TITLE_MIN_LENGTH
                && titleCompact.length() <= TITLE_MAX_LENGTH;
    }

    private RestAction<ForumPostData> createForumPost(ModalInteractionEvent event,
            User originalUser) {
        String originalMessage = event.getValue(MODAL_INPUT_ID).getAsString();

        MessageEmbed embedForPost = makeEmbedForPost(originalUser, originalMessage);

        MessageCreateData forumMessage = new MessageCreateBuilder()
            .addContent("%s has a question:".formatted(originalUser.getAsMention()))
            .setEmbeds(embedForPost)
            .build();

        String forumTitle = event.getValue(MODAL_TITLE_ID).getAsString();
        String transferQuestionTag = event.getValue(MODAL_TAG).getAsString();

        ForumChannel questionsForum = getHelperForum(event.getJDA());
        String mostCommonTag = tags.getFirst();

        String queryTag =
                StringDistances.closestMatch(transferQuestionTag, tags).orElse(mostCommonTag);

        ForumTag tag = getTagOrDefault(questionsForum.getAvailableTagsByName(queryTag, true),
                () -> questionsForum.getAvailableTagsByName(mostCommonTag, true).getFirst());

        return questionsForum.createForumPost(forumTitle, forumMessage)
            .setTags(ForumTagSnowflake.fromId(tag.getId()))
            .map(createdPost -> new ForumPostData(createdPost, originalUser));
    }

    private RestAction<Message> dmUser(MessageChannelUnion sourceChannel,
            ForumPostData forumPostData, Guild guild) {

        String messageTemplate =
                """
                        Hello%s 👋 You have asked a question in the wrong channel%s. Not a big deal, but none of the experts who could help you are reading your question there 🙁

                        Your question has been automatically transferred to %s, please continue there, thank you 👍
                        """;

        // Prevents discord from creating a distracting auto-preview for the link
        String jumpUrlSuffix = " ";
        String postUrl = forumPostData.forumPost().getMessage().getJumpUrl() + jumpUrlSuffix;

        String messageForDm = messageTemplate.formatted("", " on " + guild.getName(), postUrl);

        String messageOnDmFailure =
                messageTemplate.formatted(" " + forumPostData.author.getAsMention(), "", postUrl);

        return forumPostData.author.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(messageForDm))
            .onErrorFlatMap(error -> sourceChannel.sendMessage(messageOnDmFailure));
    }

    private RestAction<Void> deleteOriginalMessage(JDA jda, String channelId, String messageId) {
        return jda.getChannelById(MessageChannel.class, channelId).deleteMessageById(messageId);
    }

    private ForumChannel getHelperForum(JDA jda) {
        Optional<ForumChannel> forumChannelOptional = jda.getForumChannels()
            .stream()
            .filter(forumChannel -> isHelpForumName.test(forumChannel.getName()))
            .findFirst();

        return forumChannelOptional.orElseThrow(() -> new IllegalStateException(
                "Did not find the helper-forum while trying to transfer a question. Make sure the config is setup properly."));
    }

    private static ForumTag getTagOrDefault(List<ForumTag> tagsFoundOnForum,
            Supplier<ForumTag> defaultTag) {
        return tagsFoundOnForum.isEmpty() ? defaultTag.get() : tagsFoundOnForum.getFirst();
    }

    private MessageEmbed makeEmbedForPost(User originalUser, String originalMessage) {
        String avatarOrDefaultUrl = originalUser.getEffectiveAvatarUrl();

        return new EmbedBuilder().setAuthor(originalUser.getName(), null, avatarOrDefaultUrl)
            .setDescription(originalMessage)
            .setColor(EMBED_COLOR)
            .build();
    }

    private record ForumPostData(ForumPost forumPost, User author) {
    }

    private boolean isBotMessageTransfer(User author) {
        return author.isBot();
    }

    private void handleBotMessageTransfer(MessageContextInteractionEvent event) {
        event.reply("Cannot transfer messages from a bot.").setEphemeral(true).queue();
    }

    private boolean isQuestionTooShort(String question) {
        return question.length() < INPUT_MIN_LENGTH;
    }

    private boolean isInvalidForTransfer(MessageContextInteractionEvent event) {
        User author = event.getTarget().getAuthor();

        if (isBotMessageTransfer(author)) {
            handleBotMessageTransfer(event);
            return true;
        }
        return false;
    }

    private String getMessageUptoMaxLimit(String originalMessage) {
        return originalMessage.length() > INPUT_MAX_LENGTH
                ? originalMessage.substring(0, INPUT_MAX_LENGTH)
                : originalMessage;
    }
}
