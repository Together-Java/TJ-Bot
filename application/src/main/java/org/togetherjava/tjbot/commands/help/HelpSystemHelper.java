package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpThreads;
import org.togetherjava.tjbot.db.generated.tables.records.HelpThreadsRecord;

import java.awt.Color;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    public HelpSystemHelper(@NotNull Config config, @NotNull Database database) {
        HelpSystemConfig helpConfig = config.getHelpSystem();
        this.database = database;

        overviewChannelPattern = helpConfig.getOverviewChannelPattern();
        isOverviewChannelName = Pattern.compile(overviewChannelPattern).asMatchPredicate();

        stagingChannelPattern = helpConfig.getStagingChannelPattern();
        isStagingChannelName = Pattern.compile(stagingChannelPattern).asMatchPredicate();

        categoryRoleSuffix = helpConfig.getCategoryRoleSuffix();
    }

    RestAction<Message> sendExplanationMessage(@NotNull MessageChannel threadChannel) {
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

    private static @NotNull MessageEmbed embedWith(@NotNull CharSequence message) {
        return embedWith(message, null);
    }

    private static @NotNull MessageEmbed embedWith(@NotNull CharSequence message,
            @Nullable String imageUrl) {
        return new EmbedBuilder().setColor(AMBIENT_COLOR)
            .setDescription(message)
            .setImage(imageUrl)
            .build();
    }

    @NotNull
    Optional<Role> handleFindRoleForCategory(@NotNull String category, @NotNull Guild guild) {
        String roleName = category + categoryRoleSuffix;
        Optional<Role> maybeHelperRole = guild.getRolesByName(roleName, true).stream().findAny();

        if (maybeHelperRole.isEmpty()) {
            logger.warn("Unable to find the helper role '{}'.", roleName);
        }

        return maybeHelperRole;
    }

    @NotNull
    Optional<String> getCategoryOfChannel(@NotNull Channel channel) {
        return Optional.ofNullable(HelpThreadName.ofChannelName(channel.getName()).category);
    }

    @NotNull
    RestAction<Void> renameChannelToCategory(@NotNull GuildChannel channel,
            @NotNull String category) {
        HelpThreadName currentName = HelpThreadName.ofChannelName(channel.getName());
        HelpThreadName nextName =
                new HelpThreadName(currentName.activity, category, currentName.title);

        return renameChannel(channel, currentName, nextName);
    }

    @NotNull
    RestAction<Void> renameChannelToTitle(@NotNull GuildChannel channel, @NotNull String title) {
        HelpThreadName currentName = HelpThreadName.ofChannelName(channel.getName());
        HelpThreadName nextName =
                new HelpThreadName(currentName.activity, currentName.category, title);

        return renameChannel(channel, currentName, nextName);
    }

    @NotNull
    RestAction<Void> renameChannelToActivity(@NotNull GuildChannel channel,
            @NotNull ThreadActivity activity) {
        HelpThreadName currentName = HelpThreadName.ofChannelName(channel.getName());
        HelpThreadName nextName =
                new HelpThreadName(activity, currentName.category, currentName.title);

        return renameChannel(channel, currentName, nextName);
    }

    @NotNull
    private RestAction<Void> renameChannel(@NotNull GuildChannel channel,
            @NotNull HelpThreadName currentName, @NotNull HelpThreadName nextName) {
        if (currentName.equals(nextName)) {
            // Do not stress rate limits if no actual change is done
            return new CompletedRestAction<>(channel.getJDA(), null);
        }

        return channel.getManager().setName(nextName.toChannelName());
    }

    boolean isOverviewChannelName(@NotNull String channelName) {
        return isOverviewChannelName.test(channelName);
    }

    @NotNull
    String getOverviewChannelPattern() {
        return overviewChannelPattern;
    }

    boolean isStagingChannelName(@NotNull String channelName) {
        return isStagingChannelName.test(channelName);
    }

    @NotNull
    String getStagingChannelPattern() {
        return stagingChannelPattern;
    }

    static boolean isTitleValid(@NotNull CharSequence title) {
        String titleCompact = TITLE_COMPACT_REMOVAL_PATTERN.matcher(title).replaceAll("");

        return titleCompact.length() >= TITLE_COMPACT_LENGTH_MIN
                && titleCompact.length() <= TITLE_COMPACT_LENGTH_MAX
                && !titleCompact.toLowerCase(Locale.US).contains("help");
    }

    @NotNull
    Optional<TextChannel> handleRequireOverviewChannelForAsk(@NotNull Guild guild,
            @NotNull MessageChannel respondTo) {
        Predicate<String> isChannelName = this::isOverviewChannelName;
        String channelPattern = getOverviewChannelPattern();

        Optional<TextChannel> maybeChannel = guild.getTextChannelCache()
            .stream()
            .filter(channel -> isChannelName.test(channel.getName()))
            .findAny();

        if (maybeChannel.isEmpty()) {
            logger.warn(
                    "Attempted to create a help thread, did not find the overview channel matching the configured pattern '{}' for guild '{}'",
                    channelPattern, guild.getName());

            respondTo.sendMessage(
                    "Sorry, I was unable to locate the overview channel. The server seems wrongly configured, please contact a moderator.")
                .queue();
            return Optional.empty();
        }

        return maybeChannel;
    }

    record HelpThreadName(@Nullable ThreadActivity activity, @Nullable String category,
            @NotNull String title) {
        static @NotNull HelpThreadName ofChannelName(@NotNull CharSequence channelName) {
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

        @NotNull
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

        ThreadActivity(@NotNull String symbol) {
            this.symbol = symbol;
        }

        public @NotNull String getSymbol() {
            return symbol;
        }

        static @NotNull ThreadActivity ofSymbol(@NotNull String symbol) {
            return Stream.of(values())
                .filter(activity -> activity.getSymbol().equals(symbol))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown thread activity symbol: " + symbol));
        }
    }
}
