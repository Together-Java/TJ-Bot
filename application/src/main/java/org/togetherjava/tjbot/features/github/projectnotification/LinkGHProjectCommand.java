package org.togetherjava.tjbot.features.github.projectnotification;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.GhLinkedProjects;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

/**
 * This slash command (/link-gh-project) is used to link a project posted in #projects to a GitHub
 * repository associated with the project.
 *
 * The association created is: 1. Channel ID 2. GitHub repository details (owner, name)
 *
 * These details are stored within the GH_LINKED_PROJECTS table.
 *
 * @author Suraj Kumar
 */
public class LinkGHProjectCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(LinkGHProjectCommand.class);
    private static final String COMMAND_NAME = "link-gh-project";
    private static final String REPOSITORY_OWNER_OPTION = "Repository Owner";
    private static final String REPOSITORY_NAME_OPTION = "Repository Name";
    private final Database database;
    private final PullRequestFetcher pullRequestFetcher;

    /**
     * Creates a new LinkGHProjectCommand.
     *
     * There are 2 required options which are bound to this command:
     * <ul>
     * <li>"Repository Owner" the owner/organisation name that owns the repository</li>
     * <li>"Repository Name" the name of the repository as seen on GitHub</li>
     * </ul>
     *
     * @param githubPersonalAccessToken A personal access token used to authenticate against the
     *        GitHub API
     * @param database the database to store linked projects
     */
    public LinkGHProjectCommand(String githubPersonalAccessToken, Database database) {
        super(COMMAND_NAME, "description", CommandVisibility.GUILD);

        this.database = database;
        this.pullRequestFetcher = new PullRequestFetcher(githubPersonalAccessToken);

        getData().addOption(OptionType.STRING, REPOSITORY_OWNER_OPTION,
                "The repository owner/organisation name", true, false);

        getData().addOption(OptionType.STRING, REPOSITORY_NAME_OPTION, "The repository name", true,
                false);
    }

    /**
     * The slash command event handler. When a user initiates the /link-gh-project command in the
     * server this method is invoked.
     *
     * The following happens when the command is invoked:
     * <ul>
     * <li>Try fetch the current PRs for the given repository. If that is unsuccessful an error
     * message is returned back to the user.</li>
     * <li>The project details are saved to the GH_LINKED_PROJECTS table. If a record already exists
     * for the given project, the value is updated with the new repository details.</li>
     * <li>A confirmation message is sent within the project thread</li>
     * </ul>
     * 
     * @param event the event that triggered this
     */
    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        logger.trace("Entry LinkGHProjectCommand#onSlashCommand");
        OptionMapping repositoryOwner = event.getOption(REPOSITORY_OWNER_OPTION);
        OptionMapping repositoryName = event.getOption(REPOSITORY_NAME_OPTION);
        Channel channel = event.getChannel();

        if (repositoryOwner == null || repositoryName == null) {
            event.reply("The repository owner and repository name must both have values").queue();
            return;
        }

        logger.trace("Received repositoryOwner={} repositoryName={} in channel {}", repositoryOwner,
                repositoryName, channel.getName());

        String repositoryOwnerValue = repositoryOwner.getAsString();
        String repositoryNameValue = repositoryName.getAsString();

        if (!pullRequestFetcher.isRepositoryAccessible(repositoryOwnerValue, repositoryNameValue)) {
            logger.info("Repository {}/{} cannot be linked as the repository is not accessible",
                    repositoryOwnerValue, repositoryNameValue);
            event.reply("Unable to access {}/{}. To link a project please ensure it is public.")
                .queue();
            logger.trace("Exit LinkGHProjectCommand#onSlashCommand");
            return;
        }

        logger.trace("Saving project details to database");
        saveProjectToDatabase(repositoryOwner.getAsString(), repositoryName.getAsString(),
                channel.getId());
        event.reply(repositoryName.getAsString() + " has been linked to this project").queue();

        logger.trace("Exit LinkGHProjectCommand#onSlashCommand");
    }

    /** Saves project details to the GH_LINKED_PROJECTS, replacing the value if it already exists */
    private void saveProjectToDatabase(String repositoryOwner, String repositoryName,
            String channelId) {

        logger.trace(
                "Entry LinkGHProjectCommand#saveProjectToDatabase repositoryOwner={} repositoryName={} channelId={}",
                repositoryOwner, repositoryName, channelId);

        GhLinkedProjects table = GhLinkedProjects.GH_LINKED_PROJECTS;

        logger.info("Saving {}/{} to database", repositoryOwner, repositoryName);

        database.write(context -> context.insertInto(table)
            .set(table.REPOSITORYNAME, repositoryName)
            .set(table.REPOSITORYOWNER, repositoryOwner)
            .set(table.CHANNELID, channelId)
            .onConflict(table.CHANNELID)
            .doUpdate()
            .set(table.REPOSITORYNAME, repositoryName)
            .set(table.REPOSITORYOWNER, repositoryOwner)
            .execute());

        logger.trace("Exit LinkGHProjectCommand#saveProjectToDatabase");
    }
}
