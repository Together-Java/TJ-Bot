package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.util.Optional;

import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MAX;
import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MIN;

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
    private static final Logger logger = LoggerFactory.getLogger(AskCommand.class);

    private static final String TITLE_OPTION = "title";
    private static final String CATEGORY_OPTION = "category";

    private final HelpSystemHelper helper;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public AskCommand(@NotNull Config config, @NotNull HelpSystemHelper helper) {
        super("ask", "Ask a question - use this in the staging channel",
                SlashCommandVisibility.GUILD);

        OptionData title =
                new OptionData(OptionType.STRING, TITLE_OPTION, "short and to the point", true);
        OptionData category = new OptionData(OptionType.STRING, CATEGORY_OPTION,
                "select what describes your question the best", true);
        config.getHelpSystem()
            .getCategories()
            .forEach(categoryText -> category.addChoice(categoryText, categoryText));

        getData().addOptions(title, category);

        this.helper = helper;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
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

        overviewChannel.createThreadChannel("[%s] %s".formatted(category, title))
            .flatMap(threadChannel -> handleEvent(eventHook, threadChannel, author, title, category,
                    guild))
            .queue(any -> {
            }, e -> handleFailure(e, eventHook));
    }

    private boolean handleIsValidTitle(@NotNull CharSequence title, @NotNull IReplyCallback event) {
        if (HelpSystemHelper.isTitleValid(title)) {
            return true;
        }

        event.reply(
                "Sorry, but the title length (after removal of special characters) has to be between %d and %d."
                    .formatted(TITLE_COMPACT_LENGTH_MIN, TITLE_COMPACT_LENGTH_MAX))
            .setEphemeral(true)
            .queue();

        return false;
    }

    private @NotNull RestAction<Message> handleEvent(@NotNull InteractionHook eventHook,
            @NotNull ThreadChannel threadChannel, @NotNull Member author, @NotNull String title,
            @NotNull String category, @NotNull Guild guild) {
        return sendInitialMessage(guild, threadChannel, author, title, category)
            .flatMap(any -> notifyUser(eventHook, threadChannel))
            .flatMap(any -> helper.sendExplanationMessage(threadChannel));
    }

    private RestAction<Message> sendInitialMessage(@NotNull Guild guild,
            @NotNull ThreadChannel threadChannel, @NotNull Member author, @NotNull String title,
            @NotNull String category) {
        String roleMentionDescription = helper.handleFindRoleForCategory(category, guild)
            .map(role -> " (%s)".formatted(role.getAsMention()))
            .orElse("");

        String contentPattern = "%s has a question about '**%s**'%%s and will send the details now."
            .formatted(author.getAsMention(), title);
        String contentWithoutRole = contentPattern.formatted("");
        String contentWithRole = contentPattern.formatted(roleMentionDescription);

        // We want to invite all members of a role, but without hard-pinging them. However,
        // manually inviting them is cumbersome and can hit rate limits.
        // Instead, we abuse the fact that a role-ping through an edit will not hard-ping users,
        // but still invite them to a thread.
        return threadChannel.sendMessage(contentWithoutRole)
            .flatMap(message -> message.editMessage(contentWithRole));
    }

    private static @NotNull RestAction<Message> notifyUser(@NotNull InteractionHook eventHook,
            @NotNull IMentionable threadChannel) {
        return eventHook.editOriginal("""
                Created a thread for you: %s
                Please ask your question there, thanks.""".formatted(threadChannel.getAsMention()));
    }

    private static void handleFailure(@NotNull Throwable exception,
            @NotNull InteractionHook eventHook) {
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
