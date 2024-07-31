package org.togetherjava.tjbot.features.github;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.DatabaseException;
import org.togetherjava.tjbot.db.generated.tables.PrNotifications;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

/**
 * Slash command to unlink a project from a channel.
 */
public class GitHubUnlinkCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GitHubUnlinkCommand.class);

    private static final String REPOSITORY_OWNER_OPTION = "owner";
    private static final String REPOSITORY_NAME_OPTION = "name";

    private final Database database;

    /**
     * Creates new GitHub unlink command.
     * 
     * @param database the database to remove linked pull request notifications
     */
    public GitHubUnlinkCommand(Database database) {
        super("unlink-gh-project", "Unlinks a GitHub repository", CommandVisibility.GUILD);
        this.database = database;

        getData()
            .addOption(OptionType.STRING, REPOSITORY_OWNER_OPTION,
                    "The owner of the repository to get unlinked", true)
            .addOption(OptionType.STRING, REPOSITORY_NAME_OPTION,
                    "The name of the repository to get unlinked", true);
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

        try {
            int deleted = deleteNotification(channelId, repositoryOwner, repositoryName);

            if (deleted == 0) {
                event.reply("The provided repository wasn't linked to this channel previously.")
                    .setEphemeral(true)
                    .queue();
            } else {
                event.reply("Successfully unlinked repository.").setEphemeral(true).queue();
            }
        } catch (DatabaseException e) {
            logger.error("Failed to delete pull request notification link from database.", e);
            event.reply("Failed to unlink repository.").setEphemeral(true).queue();
        }
    }

    private int deleteNotification(long channelId, String repositoryOwner, String repositoryName) {
        return database
            .writeAndProvide(context -> context.deleteFrom(PrNotifications.PR_NOTIFICATIONS)
                .where(PrNotifications.PR_NOTIFICATIONS.CHANNEL_ID.eq(channelId))
                .and(PrNotifications.PR_NOTIFICATIONS.REPOSITORY_OWNER.eq(repositoryOwner))
                .and(PrNotifications.PR_NOTIFICATIONS.REPOSITORY_NAME.eq(repositoryName))
                .execute());
    }

}
