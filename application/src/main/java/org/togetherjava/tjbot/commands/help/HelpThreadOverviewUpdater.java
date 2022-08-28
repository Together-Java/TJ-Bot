package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.config.Config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides and updates an overview of all active questions in an overview channel.
 * <p>
 * The process runs on a schedule, but is also triggered whenever a new question has been asked in
 * the staging channel.
 * <p>
 * Active questions are automatically picked up and grouped by categories.
 */
public final class HelpThreadOverviewUpdater extends MessageReceiverAdapter implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(HelpThreadOverviewUpdater.class);

    private static final String STATUS_TITLE = "## __**Active questions**__ ##";
    private static final int OVERVIEW_QUESTION_LIMIT = 150;
    private static final AtomicInteger FIND_STATUS_MESSAGE_CONSECUTIVE_FAILURES =
            new AtomicInteger(0);
    private static final int FIND_STATUS_MESSAGE_FAILURE_THRESHOLD = 3;

    private final HelpSystemHelper helper;
    private final List<String> allCategories;

    private static final ScheduledExecutorService UPDATE_SERVICE =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public HelpThreadOverviewUpdater(Config config, HelpSystemHelper helper) {
        super(Pattern.compile(config.getHelpSystem().getOverviewChannelPattern()));

        allCategories = config.getHelpSystem().getCategories();
        this.helper = helper;
    }

    @Override
    @Nonnull
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuildCache().forEach(this::updateOverviewForGuild);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Update whenever a thread was created
        Message message = event.getMessage();
        if (message.getType() != MessageType.THREAD_CREATED) {
            return;
        }

        // Cleanup the status messages
        message.delete().queue();

        // Thread creation can sometimes take a bit longer than the actual message, so that
        // "getThreadChannels()" would not pick it up, hence we execute the update with some slight
        // delay.
        Runnable updateOverviewCommand = () -> {
            try {
                updateOverviewForGuild(event.getGuild());
            } catch (Exception e) {
                logger.error(
                        "Unknown error while attempting to update the help overview for guild {}.",
                        event.getGuild().getId(), e);
            }
        };
        UPDATE_SERVICE.schedule(updateOverviewCommand, 2, TimeUnit.SECONDS);
    }

    private void updateOverviewForGuild(Guild guild) {
        Optional<TextChannel> maybeOverviewChannel = helper
            .handleRequireOverviewChannel(guild, channelPattern -> logger.warn(
                    "Unable to update help thread overview, did not find an overview channel matching the configured pattern '{}' for guild '{}'",
                    channelPattern, guild.getName()));

        if (maybeOverviewChannel.isEmpty()) {
            return;
        }

        updateOverview(maybeOverviewChannel.orElseThrow());
    }

    private void updateOverview(TextChannel overviewChannel) {
        logger.debug("Updating overview of active questions");

        List<ThreadChannel> activeThreads = helper.getActiveThreadsIn(overviewChannel);
        logger.debug("Found {} active questions", activeThreads.size());

        Message message = new MessageBuilder()
            .setContent(STATUS_TITLE + "\n\n" + createDescription(activeThreads))
            .build();

        getStatusMessage(overviewChannel)
            .flatMap(maybeStatusMessage -> sendUpdatedOverview(maybeStatusMessage.orElse(null),
                    message, overviewChannel))
            .queue();
    }

    @Nonnull
    private String createDescription(Collection<ThreadChannel> activeThreads) {
        if (activeThreads.isEmpty()) {
            return "Currently none.";
        }

        return activeThreads.stream()
            .sorted(Comparator.comparing(ThreadChannel::getTimeCreated).reversed())
            .limit(OVERVIEW_QUESTION_LIMIT)
            .collect(Collectors
                .groupingBy(thread -> helper.getCategoryOfChannel(thread).orElse("Uncategorized")))
            .entrySet()
            .stream()
            .map(CategoryWithThreads::ofEntry)
            .sorted(Comparator.comparingInt(categoryWithThreads -> {
                // Order based on config, unknown categories last
                int indexOfCategory = allCategories.indexOf(categoryWithThreads.category);
                if (indexOfCategory == -1) {
                    return Integer.MAX_VALUE;
                }
                return indexOfCategory;
            }))
            .map(CategoryWithThreads::toDiscordString)
            .collect(Collectors.joining("\n\n"));
    }

    @Nonnull
    private static RestAction<Optional<Message>> getStatusMessage(MessageChannel channel) {
        return channel.getHistory()
            .retrievePast(1)
            .map(messages -> messages.stream()
                .findFirst()
                .filter(HelpThreadOverviewUpdater::isStatusMessage));
    }

    private static boolean isStatusMessage(Message message) {
        if (!message.getAuthor().equals(message.getJDA().getSelfUser())) {
            return false;
        }

        String content = message.getContentRaw();
        return content.startsWith(STATUS_TITLE);
    }

    @Nonnull
    private RestAction<Message> sendUpdatedOverview(@Nullable Message statusMessage,
            Message updatedStatusMessage, MessageChannel overviewChannel) {
        logger.debug("Sending the updated question overview");
        if (statusMessage == null) {
            int currentFailures = FIND_STATUS_MESSAGE_CONSECUTIVE_FAILURES.incrementAndGet();
            if (currentFailures >= FIND_STATUS_MESSAGE_FAILURE_THRESHOLD) {
                logger.warn(
                        "Failed to locate the question overview too often ({} times), sending a fresh message instead.",
                        currentFailures);
                FIND_STATUS_MESSAGE_CONSECUTIVE_FAILURES.set(0);
                return overviewChannel.sendMessage(updatedStatusMessage);
            }

            logger.info(
                    "Failed to locate the question overview ({} times), trying again next time.",
                    currentFailures);
            return new CompletedRestAction<>(overviewChannel.getJDA(), null);
        }

        FIND_STATUS_MESSAGE_CONSECUTIVE_FAILURES.set(0);
        String statusMessageId = statusMessage.getId();
        return overviewChannel.editMessageById(statusMessageId, updatedStatusMessage);
    }

    private record CategoryWithThreads(String category, List<ThreadChannel> threads) {

        String toDiscordString() {
            String threadListText = threads.stream()
                .map(ThreadChannel::getAsMention)
                .collect(Collectors.joining("\n• ", "• ", ""));

            return "**%s**:%n%s".formatted(category, threadListText);
        }

        @Nonnull
        static CategoryWithThreads ofEntry(
                Map.Entry<String, ? extends List<ThreadChannel>> categoryAndThreads) {
            return new CategoryWithThreads(categoryAndThreads.getKey(),
                    categoryAndThreads.getValue());
        }
    }
}
