package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
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

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
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
        categories = new HashSet<>(categoriesList);
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

    RestAction<Message> sendExplanationMessage(GuildMessageChannel threadChannel) {
        MessageEmbed helpEmbed = new EmbedBuilder()
            .setDescription(
                    """
                            If nobody is calling back, that usually means that your question was **not well asked** and \
                                hence nobody feels confident enough answering. Try to use your time to elaborate, \
                                **provide details**, context, more code, examples and maybe some screenshots. \
                                With enough info, someone knows the answer for sure.""")
            .build();

        return threadChannel.sendMessageEmbeds(helpEmbed);
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
        Optional<String[]> chatGPTAnswer;

        if (questionOptional.isEmpty()) {
            return useChatGptFallbackMessage(threadChannel);
        }
        String question = questionOptional.get();
        logger.debug("The final question sent to chatGPT: {}", question);

        chatGPTAnswer = chatGptService.ask(question);
        if (chatGPTAnswer.isEmpty()) {
            return useChatGptFallbackMessage(threadChannel);
        }

        List<String> ids = new CopyOnWriteArrayList<>();
        RestAction<Message> message =
                mentionGuildSlashCommand(threadChannel.getGuild(), ChatGptCommand.COMMAND_NAME)
                    .map("""
                            Here is an AI assisted attempt to answer your question 🤖. Maybe it helps! \
                            In any case, a human is on the way 👍. To continue talking to the AI, you can use \
                            %s.
                            """::formatted)
                    .flatMap(threadChannel::sendMessage)
                    .onSuccess(m -> ids.add(m.getId()));
        String[] answers = chatGPTAnswer.orElseThrow();

        for (int i = 0; i < answers.length; i++) {
            MessageCreateAction answer = threadChannel.sendMessage(answers[i]);

            if (i == answers.length - 1) {
                message = message.flatMap(any -> answer
                    .addActionRow(generateDismissButton(componentIdInteractor, ids)));
                continue;
            }

            message = message.flatMap(ignored -> answer.onSuccess(m -> ids.add(m.getId())));
        }

        return message;
    }

    private Button generateDismissButton(ComponentIdInteractor componentIdInteractor,
            List<String> ids) {
        String buttonId = componentIdInteractor.generateComponentId(ids.toArray(String[]::new));
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

        StringBuilder tagBuilder = new StringBuilder();
        int stringLength = questionBuilder.length();
        for (ForumTag tag : threadChannel.getAppliedTags()) {
            String tagName = tag.getName();
            stringLength += tagName.length();
            if (stringLength > MAX_QUESTION_LENGTH) {
                break;
            }
            tagBuilder.append(String.format("%s ", tagName));
        }

        questionBuilder.insert(0, tagBuilder);

        return Optional.of(questionBuilder.toString());
    }

    private RestAction<Message> useChatGptFallbackMessage(ThreadChannel threadChannel) {
        return mentionGuildSlashCommand(threadChannel.getGuild(), ChatGptCommand.COMMAND_NAME)
            .map(CHATGPT_FAILURE_MESSAGE::formatted)
            .flatMap(threadChannel::sendMessage);
    }

    void writeHelpThreadToDatabase(long authorId, ThreadChannel threadChannel) {
        database.write(content -> {
            HelpThreadsRecord helpThreadsRecord = content.newRecord(HelpThreads.HELP_THREADS)
                .setAuthorId(authorId)
                .setChannelId(threadChannel.getIdLong())
                .setCreatedAt(threadChannel.getTimeCreated().toInstant());
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
            .filter(tag -> tagNamesToMatch.contains(tag.getName()))
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

        return matchingTags.get(0);
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
}
