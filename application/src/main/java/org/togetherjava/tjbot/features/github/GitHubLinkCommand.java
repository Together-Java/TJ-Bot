package org.togetherjava.tjbot.features.github;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.DatabaseException;
import org.togetherjava.tjbot.db.generated.tables.PrNotifications;
import org.togetherjava.tjbot.db.generated.tables.records.PrNotificationsRecord;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.io.IOException;

/**
 * Slash command used to link a GitHub project to a discord channel to post pull request
 * notifications.
 */
public class GitHubLinkCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLinkCommand.class);

    private static final String REPOSITORY_OWNER_OPTION = "owner";
    private static final String REPOSITORY_NAME_OPTION = "name";

    private final Database database;
    private final String githubApiKey;

    /**
     * Creates new GitHub link command.
     * 
     * @param database the database to store the new linked pull request notifications
     * @param config the config to get the GitHub API key
     */
    public GitHubLinkCommand(Database database, Config config) {
        super("link-gh-project",
                "Links a GitHub repository to this project post to receive pull request notifications",
                CommandVisibility.GUILD);
        this.database = database;
        this.githubApiKey = config.getGitHubApiKey();

        getData()
            .addOption(OptionType.STRING, REPOSITORY_OWNER_OPTION,
                    "The owner of the repository to be linked", true)
            .addOption(OptionType.STRING, REPOSITORY_NAME_OPTION,
                    "The name of the repository to be linked", true);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        OptionMapping repositoryOwnerOption = event.getOption(REPOSITORY_OWNER_OPTION);
        OptionMapping repositoryNameOption = event.getOption(REPOSITORY_NAME_OPTION);

        if (repositoryOwnerOption == null || repositoryNameOption == null) {
            event.reply("You must specify a repository owner and a repository name")
                .setEphemeral(true)
                .queue();
            return;
        }

        long channelId = event.getChannelIdLong();
        String repositoryOwner = repositoryOwnerOption.getAsString();
        String repositoryName = repositoryNameOption.getAsString();

        GitHub github;
        try {
            github = new GitHubBuilder().withOAuthToken(githubApiKey).build();
        } catch (IOException e) {
            logger.error("Failed to initialize GitHub API wrapper.", e);
            event.reply("Failed to initialize GitHub API wrapper.").setEphemeral(true).queue();
            return;
        }

        try {
            if (!isRepositoryAccessible(github, repositoryOwner, repositoryName)) {
                event.reply("Repository is not publicly available.").setEphemeral(true).queue();
                logger.info("Repository {}/{} is not accessible.", repositoryOwner, repositoryName);
                return;
            }
        } catch (IOException e) {
            logger.error("Failed to check if GitHub repository is available.", e);
            event.reply("Failed to link repository.").setEphemeral(true).queue();
            return;
        }

        try {
            saveNotificationToDatabase(channelId, repositoryOwner, repositoryName);
            event.reply("Successfully linked repository.").setEphemeral(true).queue();
        } catch (DatabaseException e) {
            logger.error("Failed to save pull request notification to database.", e);
            event.reply("Failed to link repository.").setEphemeral(true).queue();
        }
    }

    private boolean isRepositoryAccessible(GitHub github, String owner, String name)
            throws IOException {
        GHRepository repository = github.getRepository(owner + "/" + name);
        return repository != null;
    }

    private void saveNotificationToDatabase(long channelId, String repositoryOwner,
            String repositoryName) {
        database.write(context -> {
            PrNotificationsRecord prNotificationsRecord =
                    context.newRecord(PrNotifications.PR_NOTIFICATIONS);
            prNotificationsRecord.setChannelId(channelId);
            prNotificationsRecord.setRepositoryOwner(repositoryOwner);
            prNotificationsRecord.setRepositoryName(repositoryName);
            prNotificationsRecord.insert();
        });
    }
}
