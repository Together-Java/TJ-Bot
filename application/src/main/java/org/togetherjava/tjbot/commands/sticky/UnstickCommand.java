package org.togetherjava.tjbot.commands.sticky;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.db.Database;

import static org.togetherjava.tjbot.db.generated.Tables.STICKY_MESSAGE;

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
        super("unstick", "unsticks the sticked message from this channel", CommandVisibility.GUILD);

        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        long stickedMessageId = getStickedMessageId(event.getChannel());

        if (stickedMessageId == 0) {
            event.reply("There are no sticked message to this channel.").setEphemeral(true).queue();
            return;
        }

        event.getChannel().retrieveMessageById(stickedMessageId).flatMap(Message::delete).queue();
        StickyUtils.deleteSticky(database, event.getChannel());

        event.reply("The sticked message in this channel is unsticked.").setEphemeral(true).queue();
    }

    private long getStickedMessageId(Channel channel) {
        return database
            .read(context -> context.selectFrom(STICKY_MESSAGE)
                .where(STICKY_MESSAGE.CHANNEL_ID.eq(channel.getIdLong()))
                .fetchAny())
            .getMessageId();
    }
}
