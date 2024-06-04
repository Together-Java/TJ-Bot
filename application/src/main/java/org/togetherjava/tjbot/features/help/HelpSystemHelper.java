package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpThreads;
import org.togetherjava.tjbot.db.generated.tables.records.HelpThreadsRecord;
import org.togetherjava.tjbot.features.chatgpt.ChatGptCommand;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.togetherjava.tjbot.features.utils.MessageUtils.mentionGuildSlashCommand;

/**
 * Helper class offering certain methods used by the help system.
 */
public final class HelpSystemHelper {
    private static final Logger logger = LoggerFactory.getLogger(HelpSystemHelper.class);

    static final Color AMBIENT_COLOR = new Color(255, 255, 165);

    private final Predicate<String> hasTagManageRole;
    private final Predicate<String> isHelpForumName;
    private final String helpForumPattern;
    /**
     * Compares categories by how common they are, ascending. I.e., the most uncommon or specific
     * category comes first.
     */
    private final Comparator<ForumTag> byCategoryCommonnessAsc;
    private final Set<String> categories;
    private final Set<String> threadActivityTagNames;
    private final String categoryRoleSuffix;

    private final Database database;
    private final ChatGptService chatGptService;
    private static final int MAX_QUESTION_LENGTH = 200;
    private static final int MIN_QUESTION_LENGTH = 10;
    private static final String CHATGPT_FAILURE_MESSAGE =
            "You can use %s to ask ChatGPT about your question while you wait for a human to respond.";

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param database the database to store help thread metadata in
     * @param chatGptService the service used to ask ChatGPT questions via the API.
     */
    public HelpSystemHelper(Config config, Database database, ChatGptService chatGptService) {
        HelpSystemConfig helpConfig = config.getHelpSystem();
        this.database = database;
        this.chatGptService = chatGptService;

        hasTagManageRole = Pattern.compile(config.getTagManageRolePattern()).asMatchPredicate();
        helpForumPattern = helpConfig.getHelpForumPattern();
        isHelpForumName = Pattern.compile(helpForumPattern).asMatchPredicate();

        List<String> categoriesList = helpConfig.getCategories();
        categories = categoriesList.stream()
            .map(String::strip)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        categoryRoleSuffix = helpConfig.getCategoryRoleSuffix();

        Map<String, Integer> categoryToCommonDesc = IntStream.range(0, categoriesList.size())
            .boxed()
            .collect(Collectors.toMap(categoriesList::get, Function.identity()));
        byCategoryCommonnessAsc = Comparator
            .<ForumTag>comparingInt(
                    tag -> categoryToCommonDesc.getOrDefault(tag.getName(), categories.size()))
            .reversed();

        threadActivityTagNames = Arrays.stream(ThreadActivity.values())
            .map(ThreadActivity::getTagName)
            .collect(Collectors.toSet());


    }

    /**
     * Determine between the title of the thread and the first message which to send to the AI. It
     * uses a simple heuristic of length to determine if enough context exists in a question. If the
     * title is used, it must also include a question mark since the title is often used more as an
     * indicator of topic versus a question.
     *
     * @param originalQuestion The first message of the thread which originates from the question
     *        asker.
     * @param threadChannel The thread in which the question was asked.
     * @return An answer for the user from the AI service or a message indicating either an error or
     *         why the message wasn't used.
     */
    RestAction<Message> constructChatGptAttempt(ThreadChannel threadChannel,
            String originalQuestion, ComponentIdInteractor componentIdInteractor) {
        Optional<String> questionOptional = prepareChatGptQuestion(threadChannel, originalQuestion);
        Optional<String> chatGPTAnswer;

        if (questionOptional.isEmpty()) {
            return useChatGptFallbackMessage(threadChannel);
        }
        String question = questionOptional.get();
        logger.debug("The final question sent to chatGPT: {}", question);

        ForumTag defaultTag = threadChannel.getAppliedTags().getFirst();
        ForumTag matchingTag = getCategoryTagOfChannel(threadChannel).orElse(defaultTag);

        String context = matchingTag.getName();
        chatGPTAnswer = chatGptService.ask(question, context);

        if (chatGPTAnswer.isEmpty()) {
            return useChatGptFallbackMessage(threadChannel);
        }

        StringBuilder idForDismissButton = new StringBuilder();
        RestAction<Message> message =
                mentionGuildSlashCommand(threadChannel.getGuild(), ChatGptCommand.COMMAND_NAME)
                    .map("""
                            Here is an AI assisted attempt to answer your question 🤖. Maybe it helps! \
                            In any case, a human is on the way 👍. To continue talking to the AI, you can use \
                            %s.
                            """::formatted)
                    .flatMap(threadChannel::sendMessage)
                    .onSuccess(m -> idForDismissButton.append(m.getId()));

        String answer = chatGPTAnswer.orElseThrow();
        SelfUser selfUser = threadChannel.getJDA().getSelfUser();

        int responseCharLimit = MessageEmbed.DESCRIPTION_MAX_LENGTH;
        if (answer.length() > responseCharLimit) {
            answer = answer.substring(0, responseCharLimit);
        }

        MessageEmbed responseEmbed = generateGptResponseEmbed(answer, selfUser, originalQuestion);
        return message.flatMap(any -> threadChannel.sendMessageEmbeds(responseEmbed)
            .addActionRow(
                    generateDismissButton(componentIdInteractor, idForDismissButton.toString())));
    }

    /**
     * Generates a MessageEmbed for a response using AI.
     *
     * @param answer The response text generated by AI.
     * @param selfUser The SelfUser representing the bot.
     * @param title The title for the MessageEmbed.
     * @return A MessageEmbed that contains response generated by AI.
     */
    public MessageEmbed generateGptResponseEmbed(String answer, SelfUser selfUser, String title) {
        String responseByGptFooter = "- AI generated response";

        int embedTitleLimit = MessageEmbed.TITLE_MAX_LENGTH;
        String capitalizedTitle = Character.toUpperCase(title.charAt(0)) + title.substring(1);

        String titleForEmbed = capitalizedTitle.length() > embedTitleLimit
                ? capitalizedTitle.substring(0, embedTitleLimit)
                : capitalizedTitle;

        return new EmbedBuilder()
            .setAuthor(selfUser.getName(), null, selfUser.getEffectiveAvatarUrl())
            .setTitle(titleForEmbed)
            .setDescription(answer)
            .setColor(Color.pink)
            .setFooter(responseByGptFooter)
            .build();
    }

    private Button generateDismissButton(ComponentIdInteractor componentIdInteractor, String id) {
        String buttonId = componentIdInteractor.generateComponentId(id);
        return Button.danger(buttonId, "Dismiss");
    }

    private Optional<String> prepareChatGptQuestion(ThreadChannel threadChannel,
            String originalQuestion) {
        String questionTitle = threadChannel.getName();
        StringBuilder questionBuilder = new StringBuilder(MAX_QUESTION_LENGTH);

        if (originalQuestion.length() < MIN_QUESTION_LENGTH
                && questionTitle.length() < MIN_QUESTION_LENGTH) {
            return Optional.empty();
        }

        questionBuilder.append(questionTitle).append(" ");
        originalQuestion = originalQuestion.substring(0, Math
            .min(MAX_QUESTION_LENGTH - questionBuilder.length(), originalQuestion.length()));

        questionBuilder.append(originalQuestion);
        return Optional.of(questionBuilder.toString());
    }

    private RestAction<Message> useChatGptFallbackMessage(ThreadChannel threadChannel) {
        return mentionGuildSlashCommand(threadChannel.getGuild(), ChatGptCommand.COMMAND_NAME)
            .map(CHATGPT_FAILURE_MESSAGE::formatted)
            .flatMap(threadChannel::sendMessage);
    }

    void writeHelpThreadToDatabase(long authorId, ThreadChannel threadChannel) {

        Instant createdAt = threadChannel.getTimeCreated().toInstant();

        String appliedTags = threadChannel.getAppliedTags()
            .stream()
            .filter(this::shouldIgnoreTag)
            .map(ForumTag::getName)
            .collect(Collectors.joining(","));

        database.write(content -> {
            HelpThreadsRecord helpThreadsRecord = content.newRecord(HelpThreads.HELP_THREADS)
                .setAuthorId(authorId)
                .setChannelId(threadChannel.getIdLong())
                .setCreatedAt(createdAt)
                .setTags(appliedTags)
                .setTicketStatus(TicketStatus.ACTIVE.val);
            if (helpThreadsRecord.update() == 0) {
                helpThreadsRecord.insert();
            }
        });
    }

    Optional<Role> handleFindRoleForCategory(String category, Guild guild) {
        String roleName = category + categoryRoleSuffix;
        Optional<Role> maybeHelperRole = guild.getRolesByName(roleName, true).stream().findAny();

        if (maybeHelperRole.isEmpty()) {
            logger.warn("Unable to find the helper role '{}'.", roleName);
        }

        return maybeHelperRole;
    }

    RestAction<Void> renameChannel(GuildChannel channel, String title) {
        String currentTitle = channel.getName();
        if (title.equals(currentTitle)) {
            // Do not stress rate limits if no actual change is done
            return new CompletedRestAction<>(channel.getJDA(), null);
        }

        return channel.getManager().setName(title);
    }

    Optional<ForumTag> getCategoryTagOfChannel(ThreadChannel channel) {
        return getFirstMatchingTagOfChannel(categories, channel);
    }

    Optional<ForumTag> getActivityTagOfChannel(ThreadChannel channel) {
        return getFirstMatchingTagOfChannel(threadActivityTagNames, channel);
    }

    private Optional<ForumTag> getFirstMatchingTagOfChannel(Set<String> tagNamesToMatch,
            ThreadChannel channel) {
        return channel.getAppliedTags()
            .stream()
            .filter(tag -> tagNamesToMatch.contains(tag.getName().toLowerCase()))
            .min(byCategoryCommonnessAsc);
    }

    RestAction<Void> changeChannelCategory(ThreadChannel channel, String category) {
        return changeMatchingTagOfChannel(category, categories, channel);
    }

    RestAction<Void> changeChannelActivity(ThreadChannel channel, ThreadActivity activity) {
        return changeMatchingTagOfChannel(activity.getTagName(), threadActivityTagNames, channel);
    }

    private RestAction<Void> changeMatchingTagOfChannel(String tagName, Set<String> tagNamesToMatch,
            ThreadChannel channel) {
        List<ForumTag> tags = new ArrayList<>(channel.getAppliedTags());

        Optional<ForumTag> currentTag = getFirstMatchingTagOfChannel(tagNamesToMatch, channel);
        if (currentTag.isPresent()) {
            if (currentTag.orElseThrow().getName().equals(tagName)) {
                // Do not stress rate limits if no actual change is done
                return new CompletedRestAction<>(channel.getJDA(), null);
            }

            tags.remove(currentTag.orElseThrow());
        }

        ForumTag nextTag = requireTag(tagName, channel.getParentChannel().asForumChannel());
        // In case the tag was already there, but not in front, we first remove it
        tags.remove(nextTag);

        if (tags.size() >= ForumChannel.MAX_POST_TAGS) {
            // If still at max size, remove last to make place for the new tag.
            // The last tag is the least important.
            // NOTE In practice, this can happen if the user selected 5 categories and
            // the bot then tries to add the activity tag
            tags.remove(tags.size() - 1);
        }

        Collection<ForumTag> nextTags = new ArrayList<>(tags.size());
        // Tag should be in front, to take priority over others
        nextTags.add(nextTag);
        nextTags.addAll(tags);

        List<ForumTagSnowflake> tagSnowflakes =
                nextTags.stream().map(ForumTag::getIdLong).map(ForumTagSnowflake::fromId).toList();
        return channel.getManager().setAppliedTags(tagSnowflakes);
    }

    private static ForumTag requireTag(String tagName, ForumChannel forumChannel) {
        List<ForumTag> matchingTags = forumChannel.getAvailableTagsByName(tagName, false);
        if (matchingTags.isEmpty()) {
            throw new IllegalStateException("The forum %s in guild %s is missing the tag %s."
                .formatted(forumChannel.getName(), forumChannel.getGuild().getName(), tagName));
        }

        return matchingTags.getFirst();
    }

    boolean hasTagManageRole(Member member) {
        return member.getRoles().stream().map(Role::getName).anyMatch(hasTagManageRole);
    }

    boolean isHelpForumName(String channelName) {
        return isHelpForumName.test(channelName);
    }

    String getHelpForumPattern() {
        return helpForumPattern;
    }

    Optional<ForumChannel> handleRequireHelpForum(Guild guild,
            Consumer<? super String> consumeChannelPatternIfNotFound) {
        Predicate<String> isChannelName = this::isHelpForumName;
        String channelPattern = getHelpForumPattern();

        Optional<ForumChannel> maybeChannel = guild.getForumChannelCache()
            .stream()
            .filter(channel -> isChannelName.test(channel.getName()))
            .findAny();

        if (maybeChannel.isEmpty()) {
            consumeChannelPatternIfNotFound.accept(channelPattern);
        }

        return maybeChannel;
    }

    List<ThreadChannel> getActiveThreadsIn(IThreadContainer channel) {
        return channel.getThreadChannels()
            .stream()
            .filter(Predicate.not(ThreadChannel::isArchived))
            .toList();
    }

    enum ThreadActivity {
        LOW("Nobody helped yet"),
        MEDIUM("Needs attention"),
        HIGH("Active");

        private final String tagName;

        ThreadActivity(String tagName) {
            this.tagName = tagName;
        }

        public String getTagName() {
            return tagName;
        }
    }

    enum TicketStatus {
        ARCHIVED(0),
        ACTIVE(1);

        final int val;

        TicketStatus(int val) {
            this.val = val;
        }
    }

    Optional<Long> getAuthorByHelpThreadId(final long channelId) {

        logger.debug("Looking for thread-record using channel ID: {}", channelId);

        return database.read(context -> context.select(HelpThreads.HELP_THREADS.AUTHOR_ID)
            .from(HelpThreads.HELP_THREADS)
            .where(HelpThreads.HELP_THREADS.CHANNEL_ID.eq(channelId))
            .fetchOptional(HelpThreads.HELP_THREADS.AUTHOR_ID));
    }


    /**
     * will be used to filter a tag based on categories config
     * 
     * @param tag applied tag
     * @return boolean result whether to ignore this tag or not
     */
    boolean shouldIgnoreTag(ForumTag tag) {
        return this.categories.contains(tag.getName().toLowerCase());
    }
}
