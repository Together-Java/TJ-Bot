package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpThreads;
import org.togetherjava.tjbot.db.generated.tables.records.HelpThreadsRecord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Helper class offering certain methods used by the help system.
 */
public final class HelpSystemHelper {
    private static final Logger logger = LoggerFactory.getLogger(HelpSystemHelper.class);

    static final Color AMBIENT_COLOR = new Color(255, 255, 165);

    private static final String CODE_SYNTAX_EXAMPLE_PATH = "codeSyntaxExample.png";

    private static final String ACTIVITY_GROUP = "activity";
    private static final String CATEGORY_GROUP = "category";
    private static final String TITLE_GROUP = "title";
    private static final Pattern EXTRACT_HELP_NAME_PATTERN =
            Pattern.compile("(?:(?<%s>\\W) )?(?:\\[(?<%s>[^\\[]+)] )?(?<%s>.+)"
                .formatted(ACTIVITY_GROUP, CATEGORY_GROUP, TITLE_GROUP));

    private static final Pattern TITLE_COMPACT_REMOVAL_PATTERN = Pattern.compile("\\W");
    static final int TITLE_COMPACT_LENGTH_MIN = 2;
    static final int TITLE_COMPACT_LENGTH_MAX = 70;

    private final Predicate<String> isOverviewChannelName;
    private final String overviewChannelPattern;
    private final Predicate<String> isStagingChannelName;
    private final String stagingChannelPattern;
    private final String categoryRoleSuffix;
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param database the database to store help thread metadata in
     */
    public HelpSystemHelper(Config config, Database database) {
        HelpSystemConfig helpConfig = config.getHelpSystem();
        this.database = database;

        overviewChannelPattern = helpConfig.getOverviewChannelPattern();
        isOverviewChannelName = Pattern.compile(overviewChannelPattern).asMatchPredicate();

        stagingChannelPattern = helpConfig.getStagingChannelPattern();
        isStagingChannelName = Pattern.compile(stagingChannelPattern).asMatchPredicate();

        categoryRoleSuffix = helpConfig.getCategoryRoleSuffix();
    }

    @Nonnull
    RestAction<Message> sendExplanationMessage(MessageChannel threadChannel) {
        boolean useCodeSyntaxExampleImage = true;
        InputStream codeSyntaxExampleData =
                AskCommand.class.getResourceAsStream("/" + CODE_SYNTAX_EXAMPLE_PATH);
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
                        "Don't forget to close your thread using the command **/close** when your question has been answered, thanks."));

        MessageAction action = threadChannel.sendMessage(message);
        if (useCodeSyntaxExampleImage) {
            action = action.addFile(codeSyntaxExampleData, CODE_SYNTAX_EXAMPLE_PATH);
        }
        return action.setEmbeds(embeds);
    }

    void writeHelpThreadToDatabase(Member author, ThreadChannel threadChannel) {
        database.write(content -> {
            HelpThreadsRecord helpThreadsRecord = content.newRecord(HelpThreads.HELP_THREADS)
                .setAuthorId(author.getIdLong())
                .setChannelId(threadChannel.getIdLong())
                .setCreatedAt(threadChannel.getTimeCreated().toInstant());
            if (helpThreadsRecord.update() == 0) {
                helpThreadsRecord.insert();
            }
        });
    }

    @Nonnull
    private static MessageEmbed embedWith(CharSequence message) {
        return embedWith(message, null);
    }

    @Nonnull
    private static MessageEmbed embedWith(CharSequence message, @Nullable String imageUrl) {
        return new EmbedBuilder().setColor(AMBIENT_COLOR)
            .setDescription(message)
            .setImage(imageUrl)
            .build();
    }

    @Nonnull
    Optional<Role> handleFindRoleForCategory(String category, Guild guild) {
        String roleName = category + categoryRoleSuffix;
        Optional<Role> maybeHelperRole = guild.getRolesByName(roleName, true).stream().findAny();

        if (maybeHelperRole.isEmpty()) {
            logger.warn("Unable to find the helper role '{}'.", roleName);
        }

        return maybeHelperRole;
    }

    @Nonnull
    Optional<String> getCategoryOfChannel(Channel channel) {
        return Optional.ofNullable(HelpThreadName.ofChannelName(channel.getName()).category);
    }

    @Nonnull
    RestAction<Void> renameChannelToCategory(GuildChannel channel, String category) {
        HelpThreadName currentName = HelpThreadName.ofChannelName(channel.getName());
        HelpThreadName nextName =
                new HelpThreadName(currentName.activity, category, currentName.title);

        return renameChannel(channel, currentName, nextName);
    }

    @Nonnull
    RestAction<Void> renameChannelToTitle(GuildChannel channel, String title) {
        HelpThreadName currentName = HelpThreadName.ofChannelName(channel.getName());
        HelpThreadName nextName =
                new HelpThreadName(currentName.activity, currentName.category, title);

        return renameChannel(channel, currentName, nextName);
    }

    @Nonnull
    RestAction<Void> renameChannelToActivity(GuildChannel channel, ThreadActivity activity) {
        HelpThreadName currentName = HelpThreadName.ofChannelName(channel.getName());
        HelpThreadName nextName =
                new HelpThreadName(activity, currentName.category, currentName.title);

        return renameChannel(channel, currentName, nextName);
    }

    @Nonnull
    private RestAction<Void> renameChannel(GuildChannel channel, HelpThreadName currentName,
            HelpThreadName nextName) {
        if (currentName.equals(nextName)) {
            // Do not stress rate limits if no actual change is done
            return new CompletedRestAction<>(channel.getJDA(), null);
        }

        return channel.getManager().setName(nextName.toChannelName());
    }

    boolean isOverviewChannelName(String channelName) {
        return isOverviewChannelName.test(channelName);
    }

    @Nonnull
    String getOverviewChannelPattern() {
        return overviewChannelPattern;
    }

    boolean isStagingChannelName(String channelName) {
        return isStagingChannelName.test(channelName);
    }

    @Nonnull
    String getStagingChannelPattern() {
        return stagingChannelPattern;
    }

    static boolean isTitleValid(CharSequence title) {
        String titleCompact = TITLE_COMPACT_REMOVAL_PATTERN.matcher(title).replaceAll("");

        return titleCompact.length() >= TITLE_COMPACT_LENGTH_MIN
                && titleCompact.length() <= TITLE_COMPACT_LENGTH_MAX
                && !titleCompact.toLowerCase(Locale.US).contains("help");
    }

    @Nonnull
    Optional<TextChannel> handleRequireOverviewChannel(Guild guild,
            Consumer<? super String> consumeChannelPatternIfNotFound) {
        Predicate<String> isChannelName = this::isOverviewChannelName;
        String channelPattern = getOverviewChannelPattern();

        Optional<TextChannel> maybeChannel = guild.getTextChannelCache()
            .stream()
            .filter(channel -> isChannelName.test(channel.getName()))
            .findAny();

        if (maybeChannel.isEmpty()) {
            consumeChannelPatternIfNotFound.accept(channelPattern);
        }

        return maybeChannel;
    }

    @Nonnull
    Optional<TextChannel> handleRequireOverviewChannelForAsk(Guild guild,
            MessageChannel respondTo) {
        return handleRequireOverviewChannel(guild, channelPattern -> {
            logger.warn(
                    "Attempted to create a help thread, did not find the overview channel matching the configured pattern '{}' for guild '{}'",
                    channelPattern, guild.getName());

            respondTo.sendMessage(
                    "Sorry, I was unable to locate the overview channel. The server seems wrongly configured, please contact a moderator.")
                .queue();
        });
    }

    @Nonnull
    List<ThreadChannel> getActiveThreadsIn(TextChannel channel) {
        return channel.getThreadChannels()
            .stream()
            .filter(Predicate.not(ThreadChannel::isArchived))
            .toList();
    }

    record HelpThreadName(@Nullable ThreadActivity activity, @Nullable String category,
            String title) {
        @Nonnull
        static HelpThreadName ofChannelName(CharSequence channelName) {
            Matcher matcher = EXTRACT_HELP_NAME_PATTERN.matcher(channelName);

            if (!matcher.matches()) {
                throw new AssertionError("Pattern must match any thread name");
            }

            String activityText = matcher.group(ACTIVITY_GROUP);

            ThreadActivity activity =
                    activityText == null ? null : ThreadActivity.ofSymbol(activityText);
            String category = matcher.group(CATEGORY_GROUP);
            String title = matcher.group(TITLE_GROUP);

            return new HelpThreadName(activity, category, title);
        }

        @Nonnull
        String toChannelName() {
            String activityText = activity == null ? "" : activity.getSymbol() + " ";
            String categoryText = category == null ? "" : "[%s] ".formatted(category);

            return activityText + categoryText + title;
        }
    }

    enum ThreadActivity {
        NEEDS_HELP("🔻"),
        LIKELY_NEEDS_HELP("🔸"),
        SEEMS_GOOD("🔹");

        private final String symbol;

        ThreadActivity(String symbol) {
            this.symbol = symbol;
        }

        @Nonnull
        public String getSymbol() {
            return symbol;
        }

        @Nonnull
        static ThreadActivity ofSymbol(String symbol) {
            return Stream.of(values())
                .filter(activity -> activity.getSymbol().equals(symbol))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown thread activity symbol: " + symbol));
        }
    }
}
