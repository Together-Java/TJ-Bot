package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.Routine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Routine that periodically checks all help threads and updates their activity based on heuristics.
 * <p>
 * The activity indicates to helpers which channels are in most need of help and which likely
 * already received attention by helpers.
 */
public final class HelpThreadActivityUpdater implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(HelpThreadActivityUpdater.class);
    private static final int SCHEDULE_MINUTES = 30;
    private static final int ACTIVITY_DETERMINE_MESSAGE_LIMIT = 11;

    private final HelpSystemHelper helper;

    /**
     * Creates a new instance.
     *
     * @param helper the helper to use
     */
    public HelpThreadActivityUpdater(@NotNull HelpSystemHelper helper) {
        this.helper = helper;
    }

    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 1, SCHEDULE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        jda.getGuildCache().forEach(this::updateActivityForGuild);
    }

    private void updateActivityForGuild(@NotNull Guild guild) {
        Optional<TextChannel> maybeOverviewChannel = helper
            .handleRequireOverviewChannel(guild, channelPattern -> logger.warn(
                    "Unable to update help thread overview, did not find an overview channel matching the configured pattern '{}' for guild '{}'",
                    channelPattern, guild.getName()));

        if (maybeOverviewChannel.isEmpty()) {
            return;
        }

        logger.debug("Updating activities of active questions");

        List<ThreadChannel> activeThreads =
                helper.getActiveThreadsIn(maybeOverviewChannel.orElseThrow());
        logger.debug("Found {} active questions", activeThreads.size());

        activeThreads.forEach(this::updateActivityForThread);
    }

    private void updateActivityForThread(@NotNull ThreadChannel threadChannel) {
        determineActivity(threadChannel)
            .flatMap(
                    threadActivity -> helper.renameChannelToActivity(threadChannel, threadActivity))
            .queue();
    }

    private static @NotNull RestAction<HelpSystemHelper.ThreadActivity> determineActivity(
            MessageChannel channel) {
        return channel.getHistory().retrievePast(ACTIVITY_DETERMINE_MESSAGE_LIMIT).map(messages -> {
            if (messages.size() >= ACTIVITY_DETERMINE_MESSAGE_LIMIT) {
                // There are likely even more messages, but we hit the limit
                return HelpSystemHelper.ThreadActivity.SEEMS_GOOD;
            }

            Map<User, List<Message>> authorToMessages = messages.stream()
                .filter(Predicate.not(HelpThreadActivityUpdater::isBotMessage))
                .collect(Collectors.groupingBy(Message::getAuthor));

            boolean isThereActivity = authorToMessages.size() >= 2 && authorToMessages.values()
                .stream()
                .anyMatch(messagesByAuthor -> messagesByAuthor.size() >= 2);

            return isThereActivity ? HelpSystemHelper.ThreadActivity.LIKELY_NEEDS_HELP
                    : HelpSystemHelper.ThreadActivity.NEEDS_HELP;
        });
    }

    private static boolean isBotMessage(@NotNull Message message) {
        return message.getAuthor().equals(message.getJDA().getSelfUser());
    }
}
