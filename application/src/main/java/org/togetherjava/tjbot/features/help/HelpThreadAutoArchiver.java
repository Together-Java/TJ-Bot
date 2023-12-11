package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.Result;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.Routine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Routine, which periodically checks all help threads and archives them if there has not been any
 * recent activity.
 */
public final class HelpThreadAutoArchiver implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(HelpThreadAutoArchiver.class);
    private static final int SCHEDULE_MINUTES = 6;
    private static final Duration ARCHIVE_AFTER_INACTIVITY_OF = Duration.ofSeconds(12);

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
        return new Schedule(ScheduleMode.FIXED_RATE, 0, SCHEDULE_MINUTES, TimeUnit.SECONDS);
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

            String linkHowToAsk = "https://stackoverflow.com/help/how-to-ask";

            MessageEmbed embed = new EmbedBuilder()
                .setDescription(
                        """
                                Your question has been closed due to inactivity.

                                If it was not resolved yet, feel free to just post a message below
                                to reopen it, or create a new thread.

                                Note that usually the reason for nobody calling back is that your
                                question may have been not well asked and hence no one felt confident
                                enough answering.

                                When you reopen the thread, try to use your time to **improve the quality**
                                of the question by elaborating, providing **details**, context, all relevant code
                                snippets, any **errors** you are getting, concrete **examples** and perhaps also some
                                screenshots. Share your **attempt**, explain the **expected results** and compare
                                them to the current results.

                                Also try to make the information **easily accessible** by sharing code
                                or assignment descriptions directly on Discord, not behind a link or
                                PDF-file; provide some guidance for long code snippets and ensure
                                the **code is well formatted** and has syntax highlighting. Kindly read through
                                %s for more.

                                With enough info, someone knows the answer for sure 👍"""
                            .formatted(linkHowToAsk))
                .setColor(HelpSystemHelper.AMBIENT_COLOR)
                .build();

            handleArchiveFlow(threadChannel, embed);
        }
    }

    private static boolean shouldBeArchived(MessageChannel channel, Instant archiveAfterMoment) {
        Instant lastActivity =
                TimeUtil.getTimeCreated(channel.getLatestMessageIdLong()).toInstant();

        return lastActivity.isBefore(archiveAfterMoment);
    }

    private void handleArchiveFlow(ThreadChannel threadChannel, MessageEmbed embed) {

        Consumer<Throwable> handleFailure = error -> {
            if (error instanceof ErrorResponseException) {
                logger.warn("Unknown error occurred during help thread auto archive routine",
                        error);
            }
        };

        Function<Result<Member>, RestAction<Message>> sendEmbedWithMention =
                (member) -> threadChannel.sendMessage(member.get().getAsMention()).addEmbeds(embed);

        Function<Result<Member>, RestAction<Message>> sendEmbedWithoutMention =
                (member) -> threadChannel.sendMessageEmbeds(embed);

        threadChannel.getGuild()
            .retrieveMemberById(threadChannel.getOwnerIdLong())
            .mapToResult()
            .flatMap(member -> {
                if (member.isSuccess()) {
                    return sendEmbedWithMention.apply(member);
                }
                logger.debug("Member already left server, archiving thread without mention");
                return sendEmbedWithoutMention.apply(member);
            })
            .flatMap(any -> threadChannel.getManager().setArchived(true))
            .queue(any -> {
            }, handleFailure);
    }
}
