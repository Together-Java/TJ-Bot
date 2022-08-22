package org.togetherjava.tjbot.commands.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Fallback approach for asking questions, next to the proper way of using {@link AskCommand}.
 * <p>
 * Listens to plain messages in the staging channel, picks them up and transfers them into a proper
 * question thread.
 * <p>
 * The system can handle spam appropriately and will not create multiple threads for each message.
 * <p>
 * For example:
 *
 * <pre>
 * {@code
 * John sends: How to send emails?
 * // A thread with name "How to send emails?" is created
 * // John gets an ephemeral message saying to move to the thread instead
 * // Johns original message is deleted
 * }
 * </pre>
 */
public final class ImplicitAskListener extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ImplicitAskListener.class);

    private static final int TITLE_MAX_LENGTH = 50;

    private static final int COOLDOWN_DURATION_VALUE = 15;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.SECONDS;

    private final Cache<Long, HelpThread> userIdToLastHelpThread;
    private final HelpSystemHelper helper;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public ImplicitAskListener(@NotNull Config config, @NotNull HelpSystemHelper helper) {
        super(Pattern.compile(config.getHelpSystem().getStagingChannelPattern()));

        userIdToLastHelpThread = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
            .build();

        this.helper = helper;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Only listen to regular messages from users
        if (event.isWebhookMessage() || event.getMessage().getType() != MessageType.DEFAULT
                || event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();

        if (!handleIsNotOnCooldown(message)) {
            return;
        }

        String title = createTitle(message.getContentDisplay());

        Optional<TextChannel> maybeOverviewChannel =
                helper.handleRequireOverviewChannelForAsk(event.getGuild(), event.getChannel());
        if (maybeOverviewChannel.isEmpty()) {
            return;
        }
        TextChannel overviewChannel = maybeOverviewChannel.orElseThrow();

        overviewChannel.createThreadChannel(title)
            .flatMap(threadChannel -> handleEvent(threadChannel, message, title))
            .queue(any -> {
            }, ImplicitAskListener::handleFailure);
    }

    private boolean handleIsNotOnCooldown(@NotNull Message message) {
        Member author = message.getMember();

        Optional<HelpThread> maybeLastHelpThread =
                getLastHelpThreadIfOnCooldown(author.getIdLong());
        if (maybeLastHelpThread.isEmpty()) {
            return true;
        }

        ThreadChannel lastHelpThread = message.getGuild()
            .getThreadChannelById(maybeLastHelpThread.orElseThrow().channelId);
        String threadDescription = lastHelpThread == null ? "your previously created help thread"
                : lastHelpThread.getAsMention();

        message.getChannel()
            .sendMessage("""
                    %s Please use %s to follow up on your question, \
                    or use `/ask` to ask a new questions, thanks."""
                .formatted(author.getAsMention(), threadDescription))
            .flatMap(any -> message.delete())
            .queue();
        return false;
    }

    private Optional<HelpThread> getLastHelpThreadIfOnCooldown(long userId) {
        return Optional.ofNullable(userIdToLastHelpThread.getIfPresent(userId))
            .filter(lastHelpThread -> {
                Instant cooldownExpiration = lastHelpThread.creationTime
                    .plus(COOLDOWN_DURATION_VALUE, COOLDOWN_DURATION_UNIT);

                // If user is on cooldown
                return Instant.now().isBefore(cooldownExpiration);
            });
    }

    private static @NotNull String createTitle(@NotNull String message) {
        String titleCandidate;
        if (message.length() < TITLE_MAX_LENGTH) {
            titleCandidate = message;
        } else {
            // Attempt to end at the last word before hitting the limit
            // e.g. "[foo bar] baz" for a limit somewhere in between "baz"
            int lastWordEnd = message.lastIndexOf(' ', TITLE_MAX_LENGTH);
            if (lastWordEnd == -1) {
                lastWordEnd = TITLE_MAX_LENGTH;
            }

            titleCandidate = message.substring(0, lastWordEnd);
        }

        return HelpSystemHelper.isTitleValid(titleCandidate) ? titleCandidate : "Untitled";
    }

    private @NotNull RestAction<?> handleEvent(@NotNull ThreadChannel threadChannel,
            @NotNull Message message, @NotNull String title) {
        Member author = message.getMember();
        helper.writeHelpThreadToDatabase(author, threadChannel);
        userIdToLastHelpThread.put(author.getIdLong(),
                new HelpThread(threadChannel.getIdLong(), author.getIdLong(), Instant.now()));

        return sendInitialMessage(threadChannel, message, title)
            .flatMap(any -> notifyUser(threadChannel, message))
            .flatMap(any -> message.delete())
            .flatMap(any -> helper.sendExplanationMessage(threadChannel));
    }

    private static @NotNull RestAction<Void> inviteUsersToThread(
            @NotNull ThreadChannel threadChannel, @NotNull Member author) {
        return threadChannel.addThreadMember(author);
    }

    private static @NotNull MessageAction sendInitialMessage(@NotNull ThreadChannel threadChannel,
            @NotNull Message originalMessage, @NotNull String title) {
        String content = originalMessage.getContentRaw();
        Member author = originalMessage.getMember();

        MessageEmbed embed = new EmbedBuilder().setDescription(content)
            .setAuthor(author.getEffectiveName(), author.getEffectiveAvatarUrl(),
                    author.getEffectiveAvatarUrl())
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();

        Message threadMessage = new MessageBuilder(
                """
                        %s has a question about '**%s**' and will send the details now.

                        Please use `/change-help-category` to greatly increase the visibility of the question."""
                    .formatted(author, title)).setEmbeds(embed).build();


        return threadChannel.sendMessage(threadMessage);
    }

    private static @NotNull MessageAction notifyUser(@NotNull IMentionable threadChannel,
            @NotNull Message message) {
        return message.getChannel()
            .sendMessage(
                    """
                            %s Please use `/ask` to ask questions. Don't worry though, I created %s for you. \
                            Please continue there, thanks."""
                        .formatted(message.getAuthor().getAsMention(),
                                threadChannel.getAsMention()));
    }

    private static void handleFailure(@NotNull Throwable exception) {
        if (exception instanceof ErrorResponseException responseException) {
            ErrorResponse response = responseException.getErrorResponse();
            if (response == ErrorResponse.MAX_CHANNELS
                    || response == ErrorResponse.MAX_ACTIVE_THREADS) {
                return;
            }
        }

        logger.error("Attempted to create a help thread, but failed", exception);
    }

    private record HelpThread(long channelId, long authorId, @NotNull Instant creationTime) {
    }
}
