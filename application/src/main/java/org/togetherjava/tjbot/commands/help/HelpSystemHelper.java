package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpThreads;
import org.togetherjava.tjbot.db.generated.tables.records.HelpThreadsRecord;

import javax.annotation.Nullable;

import java.awt.Color;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Helper class offering certain methods used by the help system.
 */
public final class HelpSystemHelper {
    private static final Logger logger = LoggerFactory.getLogger(HelpSystemHelper.class);

    static final Color AMBIENT_COLOR = new Color(255, 255, 165);

    private static final String CODE_SYNTAX_EXAMPLE_PATH = "codeSyntaxExample.png";

    private final Predicate<String> isHelpForumName;
    private final String helpForumPattern;
    private final Set<String> categories;
    private final Set<String> threadActivityTagNames;
    private final String categoryRoleSuffix;
    private final Database database;
    private final JDA jda;

    /**
     * Creates a new instance.
     *
     * @param jda the JDA instance to use
     * @param config the config to use
     * @param database the database to store help thread metadata in
     */
    public HelpSystemHelper(JDA jda, Config config, Database database) {
        this.jda = jda;
        HelpSystemConfig helpConfig = config.getHelpSystem();
        this.database = database;

        helpForumPattern = helpConfig.getHelpForumPattern();
        isHelpForumName = Pattern.compile(helpForumPattern).asMatchPredicate();

        categories = new HashSet<>(helpConfig.getCategories());
        categoryRoleSuffix = helpConfig.getCategoryRoleSuffix();

        threadActivityTagNames = Arrays.stream(ThreadActivity.values())
            .map(ThreadActivity::getTagName)
            .collect(Collectors.toSet());
    }

    RestAction<Message> sendExplanationMessage(GuildMessageChannel threadChannel) {
        return MessageUtils
            .mentionGuildSlashCommand(threadChannel.getGuild(), HelpThreadCommand.COMMAND_NAME,
                    HelpThreadCommand.Subcommand.CLOSE.getCommandName())
            .flatMap(closeCommandMention -> sendExplanationMessage(threadChannel,
                    closeCommandMention));
    }

    private RestAction<Message> sendExplanationMessage(GuildMessageChannel threadChannel,
            String closeCommandMention) {
        boolean useCodeSyntaxExampleImage = true;
        InputStream codeSyntaxExampleData =
                HelpSystemHelper.class.getResourceAsStream("/" + CODE_SYNTAX_EXAMPLE_PATH);
        if (codeSyntaxExampleData == null) {
            useCodeSyntaxExampleImage = false;
        }

        String message =
                "While you are waiting for getting help, here are some tips to improve your experience:";

        List<MessageEmbed> embeds = List.of(HelpSystemHelper.embedWith(
                "Code is much easier to read if posted with **syntax highlighting** and proper formatting.",
                useCodeSyntaxExampleImage ? "attachment://" + CODE_SYNTAX_EXAMPLE_PATH : null),
                HelpSystemHelper.embedWith(
                        """
                                If your code is **long**, or you have **multiple files** to share, consider posting it on sites \
                                    like https://pastebin.com/ and share the link instead, that is easier to browse for helpers."""),
                HelpSystemHelper.embedWith(
                        """
                                If nobody is calling back, that usually means that your question was **not well asked** and \
                                    hence nobody feels confident enough answering. Try to use your time to elaborate, \
                                    **provide details**, context, more code, examples and maybe some screenshots. \
                                    With enough info, someone knows the answer for sure."""),
                HelpSystemHelper.embedWith(
                        "Don't forget to close your thread using the command %s when your question has been answered, thanks."
                            .formatted(closeCommandMention)));

        MessageCreateAction action = threadChannel.sendMessage(message);
        if (useCodeSyntaxExampleImage) {
            action = action
                .addFiles(FileUpload.fromData(codeSyntaxExampleData, CODE_SYNTAX_EXAMPLE_PATH));
        }
        return action.setEmbeds(embeds);
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

    private static MessageEmbed embedWith(CharSequence message) {
        return embedWith(message, null);
    }

    private static MessageEmbed embedWith(CharSequence message, @Nullable String imageUrl) {
        return new EmbedBuilder().setColor(AMBIENT_COLOR)
            .setDescription(message)
            .setImage(imageUrl)
            .build();
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

    private static Optional<ForumTag> getFirstMatchingTagOfChannel(Set<String> tagNamesToMatch,
            ThreadChannel channel) {
        return channel.getAppliedTags()
            .stream()
            .filter(tag -> tagNamesToMatch.contains(tag.getName()))
            .findFirst();
    }

    RestAction<Void> changeChannelCategory(ThreadChannel channel, String category) {
        return changeMatchingTagOfChannel(category, categories, channel);
    }

    RestAction<Void> changeChannelActivity(ThreadChannel channel, ThreadActivity activity) {
        return changeMatchingTagOfChannel(activity.getTagName(), threadActivityTagNames, channel);
    }

    private static RestAction<Void> changeMatchingTagOfChannel(String tagName,
            Set<String> tagNamesToMatch, ThreadChannel channel) {
        Collection<ForumTag> tags = new ArrayList<>(channel.getAppliedTags());

        Optional<ForumTag> currentTag = getFirstMatchingTagOfChannel(tagNamesToMatch, channel);
        if (currentTag.isPresent()) {
            if (currentTag.orElseThrow().getName().equals(tagName)) {
                // Do not stress rate limits if no actual change is done
                return new CompletedRestAction<>(channel.getJDA(), null);
            }

            tags.remove(currentTag.orElseThrow());
        }

        ForumTag nextTag = requireTag(tagName, channel.getParentChannel().asForumChannel());
        tags.add(nextTag);

        List<ForumTagSnowflake> tagSnowflakes =
                tags.stream().map(ForumTag::getIdLong).map(ForumTagSnowflake::fromId).toList();
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
