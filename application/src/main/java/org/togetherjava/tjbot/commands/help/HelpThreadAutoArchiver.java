package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.Routine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Routine, which periodically checks all help threads and archives them if there has not been any
 * recent activity.
 */
public final class HelpThreadAutoArchiver implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(HelpThreadAutoArchiver.class);
    private static final int SCHEDULE_MINUTES = 60;
    private static final Duration ARCHIVE_AFTER_INACTIVITY_OF = Duration.ofHours(12);

    private final HelpSystemHelper helper;

    /**
     * Creates a new instance.
     *
     * @param helper the helper to use
     */
    public HelpThreadAutoArchiver(HelpSystemHelper helper) {
        this.helper = helper;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, SCHEDULE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuildCache().forEach(this::autoArchiveForGuild);
    }

    private void autoArchiveForGuild(Guild guild) {
        Optional<ForumChannel> maybeHelpForum = helper
            .handleRequireHelpForum(guild, channelPattern -> logger.warn(
                    "Unable to auto archive help threads, did not find a help forum matching the configured pattern '{}' for guild '{}'",
                    channelPattern, guild.getName()));

        if (maybeHelpForum.isEmpty()) {
            return;
        }

        logger.debug("Auto archiving of help threads");

        List<ThreadChannel> activeThreads = helper.getActiveThreadsIn(maybeHelpForum.orElseThrow());
        logger.debug("Found {} active questions", activeThreads.size());

        Instant archiveAfterMoment = computeArchiveAfterMoment();
        activeThreads
            .forEach(activeThread -> autoArchiveForThread(activeThread, archiveAfterMoment));
    }

    private Instant computeArchiveAfterMoment() {
        return Instant.now().minus(ARCHIVE_AFTER_INACTIVITY_OF);
    }

    private void autoArchiveForThread(ThreadChannel threadChannel, Instant archiveAfterMoment) {
        if (shouldBeArchived(threadChannel, archiveAfterMoment)) {
            logger.debug("Auto archiving help thread {}", threadChannel.getId());

            MessageEmbed embed = new EmbedBuilder().setDescription("""
                    Closed the thread due to inactivity.

                    If your question was not resolved yet, feel free to just post a message \
                    to reopen it, or create a new thread. But try to improve the quality of \
                    your question to make it easier to help you 👍""")
                .setColor(HelpSystemHelper.AMBIENT_COLOR)
                .build();

            threadChannel.sendMessageEmbeds(embed)
                .flatMap(any -> threadChannel.getManager().setArchived(true))
                .queue();
        }
    }

    private static boolean shouldBeArchived(MessageChannel channel, Instant archiveAfterMoment) {
        Instant lastActivity =
                TimeUtil.getTimeCreated(channel.getLatestMessageIdLong()).toInstant();

        return lastActivity.isBefore(archiveAfterMoment);
    }
}
