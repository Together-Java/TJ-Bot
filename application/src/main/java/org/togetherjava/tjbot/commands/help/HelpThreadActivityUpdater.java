package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
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
    public HelpThreadActivityUpdater(HelpSystemHelper helper) {
        this.helper = helper;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 1, SCHEDULE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuildCache().forEach(this::updateActivityForGuild);
    }

    private void updateActivityForGuild(Guild guild) {
        Optional<ForumChannel> maybeHelpForum = helper
            .handleRequireHelpForum(guild, channelPattern -> logger.warn(
                    "Unable to update help thread activities, did not find a help forum matching the configured pattern '{}' for guild '{}'",
                    channelPattern, guild.getName()));

        if (maybeHelpForum.isEmpty()) {
            return;
        }

        logger.debug("Updating activities of active questions");

        List<ThreadChannel> activeThreads = helper.getActiveThreadsIn(maybeHelpForum.orElseThrow());
        logger.debug("Found {} active questions", activeThreads.size());

        activeThreads.forEach(this::updateActivityForThread);
    }

    private void updateActivityForThread(ThreadChannel threadChannel) {
        determineActivity(threadChannel)
            .flatMap(threadActivity -> helper.changeChannelActivity(threadChannel, threadActivity))
            .queue();
    }

    private static RestAction<HelpSystemHelper.ThreadActivity> determineActivity(
            MessageChannel channel) {
        return channel.getHistory().retrievePast(ACTIVITY_DETERMINE_MESSAGE_LIMIT).map(messages -> {
            if (messages.size() >= ACTIVITY_DETERMINE_MESSAGE_LIMIT) {
                // There are likely even more messages, but we hit the limit
                return HelpSystemHelper.ThreadActivity.HIGH;
            }

            Map<User, List<Message>> authorToMessages = messages.stream()
                .filter(Predicate.not(HelpThreadActivityUpdater::isBotMessage))
                .collect(Collectors.groupingBy(Message::getAuthor));

            boolean isThereActivity = authorToMessages.size() >= 2 && authorToMessages.values()
                .stream()
                .anyMatch(messagesByAuthor -> messagesByAuthor.size() >= 2);

            return isThereActivity ? HelpSystemHelper.ThreadActivity.MEDIUM
                    : HelpSystemHelper.ThreadActivity.LOW;
        });
    }

    private static boolean isBotMessage(Message message) {
        return message.getAuthor().equals(message.getJDA().getSelfUser());
    }
}
