package org.togetherjava.tjbot.commands.sticky;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.StickyMessageRecord;

/**
 * Implements the /unstick command which is used to remove the sticked message from this channel.
 */
public final class UnstickCommand extends SlashCommandAdapter {
    private final Database database;

    /**
     * Create new Instance.
     *
     * @param database the database to get Sticky data from
     */
    public UnstickCommand(Database database) {
        super("unstick", "Unsticks the sticked message from this channel", CommandVisibility.GUILD);

        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        StickyMessageRecord stickyMessageRecord =
                StickyUtils.getSticky(database, event.getChannel());

        if (stickyMessageRecord == null) {
            event.reply("There are no sticked message to this channel.").setEphemeral(true).queue();
            return;
        }

        event.getChannel()
            .retrieveMessageById(stickyMessageRecord.getChannelId())
            .flatMap(Message::delete)
            .queue();
        StickyUtils.deleteSticky(database, event.getChannel());

        event.reply("The sticked message in this channel is unsticked.").setEphemeral(true).queue();
    }
}
