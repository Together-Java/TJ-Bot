package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;

import java.util.regex.Pattern;

/**
 * This class updates the {@code LAST_ACTIVITY_AT} value for bookmarks. The reason this is not done
 * by always checking the messages of all bookmarked channels is speed. This makes the command more
 * snappy.
 */
public class BookmarksLastMessageUpdater extends MessageReceiverAdapter {

    private final Database database;
    private final Pattern parentChannelPattern;

    /**
     * Creates an new instance
     */
    public BookmarksLastMessageUpdater(Database database, Config config) {
        super(Pattern.compile(".*"));
        this.database = database;
        this.parentChannelPattern =
                Pattern.compile(config.getHelpSystem().getOverviewChannelPattern());
    }

    /**
     * This message handler will update the {@code LAST_ACTIVITY_AT} timestamp for bookmarks
     * corresponding to the channel the message was sent in
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromThread()) {
            ThreadChannel thread = event.getChannel().asThreadChannel();

            if (parentChannelPattern.matcher(thread.getParentChannel().getName()).matches()) {
                BookmarksHelper.updateActivity(database, thread.getIdLong());
            }
        }
    }
}
