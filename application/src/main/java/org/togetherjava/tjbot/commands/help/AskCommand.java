package org.togetherjava.tjbot.commands.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MAX;
import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MIN;
import static org.togetherjava.tjbot.db.generated.Tables.HELP_THREADS;

/**
 * Implements the {@code /ask} command, which is the main way of asking questions. The command can
 * only be used in the staging channel.
 * <p>
 * Upon use, it will create a new thread for the question and invite all helpers interested in the
 * given category to it. It will also introduce the user to the system and give a quick explanation
 * message.
 * <p>
 * The other way to ask questions is by {@link ImplicitAskListener}.
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * /ask title: How to send emails? category: Frameworks
 * // A thread with name "[Frameworks] How to send emails?" is created
 * // The asker and all "Frameworks"-helpers are invited
 * }
 * </pre>
 */
public final class AskCommand extends SlashCommandAdapter {
    private static final int COOLDOWN_DURATION_VALUE = 5;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final Logger logger = LoggerFactory.getLogger(AskCommand.class);
    public static final String COMMAND_NAME = "ask";
    private static final String TITLE_OPTION = "title";
    private static final String CATEGORY_OPTION = "category";
    private final Cache<Long, Instant> userToLastAsk = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
        .build();
    private final HelpSystemHelper helper;
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     * @param database the database to get help threads from
     */
    public AskCommand(Config config, HelpSystemHelper helper, Database database) {
        super("ask", "Ask a question - use this in the staging channel", CommandVisibility.GUILD);

        OptionData title =
                new OptionData(OptionType.STRING, TITLE_OPTION, "short and to the point", true);
        OptionData category = new OptionData(OptionType.STRING, CATEGORY_OPTION,
                "select what describes your question the best", true);
        config.getHelpSystem()
            .getCategories()
            .forEach(categoryText -> category.addChoice(categoryText, categoryText));

        getData().addOptions(title, category);

        this.helper = helper;
        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        if (isUserOnCooldown(event.getUser())) {
            sendCooldownResponse(event);
            return;
        }

        String title = event.getOption(TITLE_OPTION).getAsString();
        String category = event.getOption(CATEGORY_OPTION).getAsString();

        if (!handleIsValidTitle(title, event)) {
            return;
        }

        Optional<TextChannel> maybeOverviewChannel =
                helper.handleRequireOverviewChannelForAsk(event.getGuild(), event.getChannel());
        if (maybeOverviewChannel.isEmpty()) {
            return;
        }
        TextChannel overviewChannel = maybeOverviewChannel.orElseThrow();

        InteractionHook eventHook = event.getHook();
        Member author = event.getMember();
        Guild guild = event.getGuild();
        event.deferReply(true).queue();

        HelpSystemHelper.HelpThreadName name = new HelpSystemHelper.HelpThreadName(
                HelpSystemHelper.ThreadActivity.NEEDS_HELP, category, title);

        overviewChannel.createThreadChannel(name.toChannelName())
            .flatMap(threadChannel -> handleEvent(eventHook, threadChannel, author, title, category,
                    guild))
            .queue(any -> userToLastAsk.put(event.getUser().getIdLong(), Instant.now()),
                    e -> handleFailure(e, eventHook));
    }

    private boolean isUserOnCooldown(User user) {
        return Optional.ofNullable(userToLastAsk.getIfPresent(user.getIdLong()))
            .map(lastAction -> lastAction.plus(COOLDOWN_DURATION_VALUE, COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

    private void sendCooldownResponse(SlashCommandInteractionEvent event) {
        User user = event.getUser();

        String message =
                """
                        Sorry, you can only create a single help thread every 5 minutes. Please use your existing thread %s instead.
                        If you made a typo or similar, you can adjust the title using the command %s and the category with %s :ok_hand:""";

        RestAction<String> changeTitle = MessageUtils.mentionGuildSlashCommand(event.getGuild(),
                HelpThreadCommand.COMMAND_NAME, HelpThreadCommand.CHANGE_SUBCOMMAND_GROUP,
                HelpThreadCommand.CHANGE_TITLE_SUBCOMMAND);
        RestAction<String> changeCategory = MessageUtils.mentionGuildSlashCommand(event.getGuild(),
                HelpThreadCommand.COMMAND_NAME, HelpThreadCommand.CHANGE_SUBCOMMAND_GROUP,
                HelpThreadCommand.CHANGE_CATEGORY_OPTION);
        long lastCreatedThreadId = database
            .read(context -> context.selectFrom(HELP_THREADS)
                .where(HELP_THREADS.AUTHOR_ID.eq(user.getIdLong()))
                .orderBy(HELP_THREADS.CREATED_AT.desc())
                .fetch())
            .get(0)
            .getChannelId();

        if (lastCreatedThreadId == 0) {
            logger.warn("Can't find the last help thread created by the user with id ({})",
                    user.getId());
        }

        RestAction.allOf(changeCategory, changeTitle)
            .map(mentions -> message.formatted(MessageUtils.mentionChannelById(lastCreatedThreadId),
                    mentions.get(0), mentions.get(1)))
            .flatMap(text -> event.reply(text).setEphemeral(true))
            .queue();
    }

    private boolean handleIsValidTitle(CharSequence title, IReplyCallback event) {
        if (HelpSystemHelper.isTitleValid(title)) {
            return true;
        }

        event.reply("""
                Sorry, but your title is invalid. Please pick a title where:
                • length is between %d and %d
                • must not contain the word 'help'
                Thanks, and sorry for the inconvenience 👍
                """.formatted(TITLE_COMPACT_LENGTH_MIN, TITLE_COMPACT_LENGTH_MAX))
            .setEphemeral(true)
            .queue();

        return false;
    }

    private RestAction<Message> handleEvent(InteractionHook eventHook, ThreadChannel threadChannel,
            Member author, String title, String category, Guild guild) {
        helper.writeHelpThreadToDatabase(author, threadChannel);
        return sendInitialMessage(guild, threadChannel, author, title, category)
            .flatMap(Message::pin)
            .flatMap(any -> notifyUser(eventHook, threadChannel))
            .flatMap(any -> helper.sendExplanationMessage(threadChannel));
    }

    private RestAction<Message> sendInitialMessage(Guild guild, ThreadChannel threadChannel,
            Member author, String title, String category) {
        String roleMentionDescription = helper.handleFindRoleForCategory(category, guild)
            .map(role -> " (%s)".formatted(role.getAsMention()))
            .orElse("");

        String contentPrefix =
                "%s has a question about '**%s**'".formatted(author.getAsMention(), title);
        String contentSuffix = " and will send the details now.";
        String contentWithoutRole = contentPrefix + contentSuffix;
        String contentWithRole = contentPrefix + roleMentionDescription + contentSuffix;

        // We want to invite all members of a role, but without hard-pinging them. However,
        // manually inviting them is cumbersome and can hit rate limits.
        // Instead, we abuse the fact that a role-ping through an edit will not hard-ping users,
        // but still invite them to a thread.
        return threadChannel.sendMessage(contentWithoutRole)
            .flatMap(message -> message.editMessage(contentWithRole));
    }

    private static RestAction<Message> notifyUser(InteractionHook eventHook,
            IMentionable threadChannel) {
        return eventHook.editOriginal("""
                Created a thread for you: %s
                Please ask your question there, thanks.""".formatted(threadChannel.getAsMention()));
    }

    private static void handleFailure(Throwable exception, InteractionHook eventHook) {
        if (exception instanceof ErrorResponseException responseException) {
            ErrorResponse response = responseException.getErrorResponse();
            if (response == ErrorResponse.MAX_CHANNELS
                    || response == ErrorResponse.MAX_ACTIVE_THREADS) {
                eventHook.editOriginal(
                        "It seems that there are currently too many active questions, please try again in a few minutes.")
                    .queue();
                return;
            }
        }

        logger.error("Attempted to create a help thread, but failed", exception);
    }
}
