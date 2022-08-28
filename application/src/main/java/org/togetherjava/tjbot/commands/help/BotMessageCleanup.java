package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Routine that deletes all messages posted by the bot in the staging channel.
 *
 * This is mostly to cleanup the messages created by the fallback mechanism provided by
 * {@link ImplicitAskListener}, since those messages can not be posted ephemeral.
 *
 * Messages are deleted after a certain amount of time.
 */
public final class BotMessageCleanup implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(BotMessageCleanup.class);

    private static final int MESSAGE_HISTORY_LIMIT = 50;
    private static final Duration DELETE_MESSAGE_AFTER = Duration.ofMinutes(2);

    private final HelpSystemConfig config;
    private final Predicate<String> isStagingChannelName;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     */
    public BotMessageCleanup(Config config) {
        this.config = config.getHelpSystem();

        isStagingChannelName = Pattern.compile(config.getHelpSystem().getStagingChannelPattern())
            .asMatchPredicate();
    }

    @Override
    @Nonnull
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuildCache().forEach(this::cleanupBotMessagesForGuild);
    }

    private void cleanupBotMessagesForGuild(Guild guild) {
        Optional<TextChannel> maybeStagingChannel = handleRequireStagingChannel(guild);

        if (maybeStagingChannel.isEmpty()) {
            return;
        }

        TextChannel stagingChannel = maybeStagingChannel.orElseThrow();

        stagingChannel.getHistory()
            .retrievePast(MESSAGE_HISTORY_LIMIT)
            .flatMap(messages -> cleanupBotMessages(stagingChannel, messages))
            .queue();
    }

    @Nonnull
    private Optional<TextChannel> handleRequireStagingChannel(Guild guild) {
        Optional<TextChannel> maybeChannel = guild.getTextChannelCache()
            .stream()
            .filter(channel -> isStagingChannelName.test(channel.getName()))
            .findAny();

        if (maybeChannel.isEmpty()) {
            logger.warn(
                    "Unable to cleanup bot messages, did not find a the staging channel matching the configured pattern '{}' for guild '{}'",
                    config.getStagingChannelPattern(), guild.getName());
            return Optional.empty();
        }

        return maybeChannel;
    }

    private static boolean shouldMessageBeCleanedUp(Message message) {
        if (!message.getAuthor().isBot()) {
            return false;
        }

        OffsetDateTime lastTouched =
                message.isEdited() ? message.getTimeEdited() : message.getTimeCreated();
        Instant deleteWhen = lastTouched.toInstant().plus(DELETE_MESSAGE_AFTER);

        return deleteWhen.isBefore(Instant.now());
    }

    @Nonnull
    private static RestAction<Void> cleanupBotMessages(GuildMessageChannel channel,
            Collection<? extends Message> messages) {
        logger.debug("Cleaning up old bot messages in the staging channel");
        List<String> messageIdsToDelete = messages.stream()
            .filter(BotMessageCleanup::shouldMessageBeCleanedUp)
            .map(Message::getId)
            .toList();

        logger.debug("Found {} messages to delete", messageIdsToDelete.size());

        if (messageIdsToDelete.isEmpty()) {
            return new CompletedRestAction<>(channel.getJDA(), null);
        }

        if (messageIdsToDelete.size() == 1) {
            return channel.deleteMessageById(messageIdsToDelete.get(0));
        }

        return channel.deleteMessagesByIds(messageIdsToDelete);
    }
}
