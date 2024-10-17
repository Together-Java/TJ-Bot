package org.togetherjava.tjbot.features.github.projectnotification;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.GhLinkedProjects;
import org.togetherjava.tjbot.db.generated.tables.records.GhLinkedProjectsRecord;
import org.togetherjava.tjbot.features.Routine;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The regularly, scheduled routine that checks for pull requests and reports their status. The way
 * this works is that we poll the GitHub API and send a message to the discord project channel with
 * the information. Any PRs that were created before the `lastRun` time are ignored. We only notify
 * on newly created PRs since the last run of this routine.
 *
 * @author Suraj Kumar
 */
public class ProjectPRNotifierRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(ProjectPRNotifierRoutine.class);
    private static final int SCHEDULE_TIME_IN_MINUTES = 10;
    private final Database database;
    private final PullRequestFetcher pullRequestFetcher;
    private OffsetDateTime lastRun;

    /**
     * Constructs a new ProjectPRNotifierRoutine
     *
     * @param githubPersonalAccessToken The PAT used to authenticate against the GitHub API
     * @param database The database object to store project information in
     */
    public ProjectPRNotifierRoutine(String githubPersonalAccessToken, Database database) {
        this.database = database;
        this.pullRequestFetcher = new PullRequestFetcher(githubPersonalAccessToken);
        this.lastRun = OffsetDateTime.now();
    }

    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, SCHEDULE_TIME_IN_MINUTES, TimeUnit.SECONDS);
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        logger.trace("Entry ProjectPRNotifierRoutine#runRoutine");
        List<GhLinkedProjectsRecord> projects = getAllProjects();
        logger.trace("Running routine, against {} projects", projects.size());
        for (GhLinkedProjectsRecord project : projects) {
            String channelId = project.getChannelid();
            String repositoryOwner = project.getRepositoryowner();
            String repositoryName = project.getRepositoryname();
            logger.debug("Searching for pull requests for {}/{} for channel {}", repositoryOwner,
                    repositoryName, channelId);
            if (pullRequestFetcher.isRepositoryAccessible(repositoryOwner, repositoryName)) {
                List<PullRequest> pullRequests =
                        pullRequestFetcher.fetchPullRequests(repositoryOwner, repositoryName);
                logger.debug("Found {} pull requests in {}/{}", pullRequests.size(),
                        repositoryOwner, repositoryName);
                for (PullRequest pullRequest : pullRequests) {
                    if (pullRequest.createdAt().isAfter(lastRun)) {
                        logger.info("Found new PR for {}, sending information to discord",
                                channelId);
                        sendNotificationToProject(channelId, jda, pullRequest);
                    }
                }
            } else {
                logger.warn("{}/{} is not accessible", repositoryOwner, repositoryName);
            }
        }
        lastRun = OffsetDateTime.now();
        logger.debug("lastRun has been set to {}", lastRun);
        logger.trace("Exit ProjectPRNotifierRoutine#runRoutine");
    }

    private void sendNotificationToProject(String channelId, JDA jda, PullRequest pullRequest) {
        logger.trace(
                "Entry ProjectPRNotifierRoutine#sendNotificationToProject, channelId={}, pullRequest={}",
                channelId, pullRequest);
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            logger.trace("Sending PR notification to channel {}", channel);
            channel.sendMessage("PR from " + pullRequest.user().name()).queue();
        } else {
            logger.warn("No channel found for channelId {}, pull request {}", channelId,
                    pullRequest.htmlUrl());
        }
        logger.trace("Exit ProjectPRNotifierRoutine#sendNotificationToProject");
    }

    private List<GhLinkedProjectsRecord> getAllProjects() {
        logger.trace("Entry ProjectPRNotifierRoutine#getAllProjects");
        try {
            return database
                .read(dsl -> dsl.selectFrom(GhLinkedProjects.GH_LINKED_PROJECTS).fetch());
        } finally {
            logger.trace("Exit ProjectPRNotifierRoutine#getAllProjects");
        }
    }
}
