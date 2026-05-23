package org.togetherjava.tjbot.features.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
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
import org.togetherjava.tjbot.features.chatgpt.ChatGptModel;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;
import org.togetherjava.tjbot.features.chatgpt.schema.Property;
import org.togetherjava.tjbot.features.chatgpt.schema.ResponseSchema;
import org.togetherjava.tjbot.features.chatgpt.schema.Type;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * This command transfers a question asked in any channel to the helper forum. The AI generates the
 * post title and selects the most fitting tag; the original message body is reused verbatim. On
 * success the original message is deleted and its author is notified and redirected to the new
 * post.
 */
public final class TransferQuestionCommand extends BotCommandAdapter
        implements MessageContextCommand {
    private static final Logger logger = LoggerFactory.getLogger(TransferQuestionCommand.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String COMMAND_NAME = "transfer-question";
    private static final int TITLE_MAX_LENGTH = 50;
    private static final Pattern TITLE_GUESS_COMPACT_REMOVAL_PATTERN = Pattern.compile("\\W");
    private static final int TITLE_MIN_LENGTH = 3;
    private static final Color EMBED_COLOR = new Color(50, 164, 168);

    private final Predicate<String> isHelpForumName;
    private final List<String> tags;
    private final ChatGptService chatGptService;
    private final ResponseSchema transferSchema;

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
        this.transferSchema = new ResponseSchema(
                Map.of("title", Property.of(Type.STRING), "tag", Property.of(Type.STRING)),
                List.of("title", "tag"));
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        if (isInvalidForTransfer(event)) {
            return;
        }

        event.deferReply(true).queue();

        Message message = event.getTarget();
        String authorId = message.getAuthor().getId();
        String messageId = message.getId();
        String channelId = message.getChannelId();
        String content = message.getContentRaw();

        AiTransferResult ai = askAi(content)
            .orElseGet(() -> new AiTransferResult(createTitle(content), tags.getFirst()));
        String title = sanitizeTitle(ai.title());
        String tag = ai.tag();

        Consumer<Message> notHandledAction =
                _ -> transferFlow(event, channelId, authorId, messageId, title, tag, content);

        Consumer<Throwable> handledAction = failure -> {
            if (failure instanceof ErrorResponseException errorResponseException
                    && errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                alreadyHandled(event);
                return;
            }
            logger.warn("Unknown error occurred during question transfer.", failure);
        };

        event.getChannel().retrieveMessageById(messageId).queue(notHandledAction, handledAction);
    }

    private Optional<AiTransferResult> askAi(String content) {
        String prompt = """
                Summarize the following question into a concise title (max 5 words, no quotes) \
                and pick the single most fitting tag from this list: %s.

                Question: %s
                """.formatted(tags, content);

        return chatGptService.askRaw(prompt, ChatGptModel.FAST, transferSchema)
            .flatMap(this::parseAi);
    }

    private Optional<AiTransferResult> parseAi(String json) {
        try {
            return Optional.of(OBJECT_MAPPER.readValue(json, AiTransferResult.class));
        } catch (Exception e) {
            logger.warn("Failed to parse AI transfer response: {}", json, e);
            return Optional.empty();
        }
    }

    private static String sanitizeTitle(String raw) {
        String title = raw;
        if (title.startsWith("\"") && title.endsWith("\"")) {
            title = title.substring(1, title.length() - 1);
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }
        return title;
    }

    private void transferFlow(MessageContextInteractionEvent event, String channelId,
            String authorId, String messageId, String title, String tag, String content) {
        Function<ForumPostData, WebhookMessageCreateAction<Message>> sendMessageToTransferrer =
                post -> event.getHook()
                    .sendMessage("Transferred to %s"
                        .formatted(post.forumPost.getThreadChannel().getAsMention()));

        event.getJDA()
            .retrieveUserById(authorId)
            .flatMap(fetchedUser -> createForumPost(event, fetchedUser, title, tag, content))
            .flatMap(createdForumPost -> dmUser(event.getChannel(), createdForumPost,
                    event.getGuild())
                .and(sendMessageToTransferrer.apply(createdForumPost)))
            .flatMap(dmSent -> deleteOriginalMessage(event.getJDA(), channelId, messageId))
            .queue();
    }

    private void alreadyHandled(MessageContextInteractionEvent event) {
        ForumChannel helperForum = getHelperForum(event.getJDA());
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

    private RestAction<ForumPostData> createForumPost(MessageContextInteractionEvent event,
            User originalUser, String title, String tagQuery, String content) {
        MessageEmbed embedForPost = makeEmbedForPost(originalUser, content);

        MessageCreateData forumMessage = new MessageCreateBuilder()
            .addContent("%s has a question:".formatted(originalUser.getAsMention()))
            .setEmbeds(embedForPost)
            .build();

        ForumChannel questionsForum = getHelperForum(event.getJDA());
        String mostCommonTag = tags.getFirst();

        String queryTag = StringDistances.closestMatch(tagQuery, tags).orElse(mostCommonTag);

        ForumTag tag = getTagOrDefault(questionsForum.getAvailableTagsByName(queryTag, true),
                () -> questionsForum.getAvailableTagsByName(mostCommonTag, true).getFirst());

        return questionsForum.createForumPost(title, forumMessage)
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

    private record AiTransferResult(String title, String tag) {
    }

    private boolean isBotMessageTransfer(User author) {
        return author.isBot();
    }

    private void handleBotMessageTransfer(MessageContextInteractionEvent event) {
        event.reply("Cannot transfer messages from a bot.").setEphemeral(true).queue();
    }

    private boolean isInvalidForTransfer(MessageContextInteractionEvent event) {
        User author = event.getTarget().getAuthor();

        if (isBotMessageTransfer(author)) {
            handleBotMessageTransfer(event);
            return true;
        }
        return false;
    }
}
