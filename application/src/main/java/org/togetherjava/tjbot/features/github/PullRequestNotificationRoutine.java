package org.togetherjava.tjbot.features.github;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.PrNotifications;
import org.togetherjava.tjbot.db.generated.tables.records.PrNotificationsRecord;
import org.togetherjava.tjbot.features.Routine;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Routine to send a notification about new pull request.
 */
public class PullRequestNotificationRoutine implements Routine {

    private static final Logger logger =
            LoggerFactory.getLogger(PullRequestNotificationRoutine.class);

    private final Database database;
    private final String githubApiKey;
    private Date lastExecution;

    /**
     * Creates new notification routine.
     *
     * @param database the database to get the pull request notifications
     * @param config the config to get the GitHub API key
     */
    public PullRequestNotificationRoutine(Database database, Config config) {
        this.database = database;
        this.githubApiKey = config.getGitHubApiKey();
        this.lastExecution = new Date();
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 15, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(JDA jda) {
        GitHub github;
        try {
            github = new GitHubBuilder().withOAuthToken(githubApiKey).build();
        } catch (IOException e) {
            logger.error("Failed to initialize GitHub API wrapper.", e);
            return;
        }

        for (PrNotificationsRecord notification : getAllNotifications()) {
            long channelId = notification.getChannelId();
            String repositoryOwner = notification.getRepositoryOwner();
            String repositoryName = notification.getRepositoryName();

            try {
                GHRepository repository =
                        github.getRepository(repositoryOwner + "/" + repositoryName);

                if (repository == null) {
                    logger.info("Failed to find repository {}/{}.", repositoryOwner,
                            repositoryName);
                    continue;
                }

                List<GHPullRequest> pullRequests = repository.getPullRequests(GHIssueState.OPEN);
                for (GHPullRequest pr : pullRequests) {
                    if (pr.getCreatedAt().after(lastExecution)) {
                        sendNotification(jda, channelId, pr);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to send notification for repository {}/{}.", repositoryOwner,
                        repositoryName, e);
            }
        }

        lastExecution = new Date();
    }

    private List<PrNotificationsRecord> getAllNotifications() {
        return database
            .read(context -> context.selectFrom(PrNotifications.PR_NOTIFICATIONS).fetch());
    }

    private void sendNotification(JDA jda, long channelId, GHPullRequest pr) throws IOException {
        ThreadChannel channel = jda.getThreadChannelById(channelId);
        if (channel == null) {
            logger.info("Failed to find channel {} to send pull request notification.", channelId);
            return;
        }
        channel.sendMessage("New pull request from " + pr.getUser().getLogin() + ".").queue();
    }

}
