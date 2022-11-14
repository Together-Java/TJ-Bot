package org.togetherjava.tjbot.commands.sticky;

import net.dv8tion.jda.api.entities.channel.Channel;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.StickyMessageRecord;

import javax.annotation.Nullable;

import static org.togetherjava.tjbot.db.generated.Tables.STICKY_MESSAGE;

/**
 * Utility for Sticky.
 */
public class StickyUtils {
    /**
     * Gets the sticky for the given channel.
     *
     * @param database the database to get the sticky from
     * @param channel the channel to get the sticky of
     * @return the StickyMessageRecord for this channel
     */
    @Nullable
    public static StickyMessageRecord getSticky(Database database, Channel channel) {
        return database.read(context -> context.selectFrom(STICKY_MESSAGE)
            .where(STICKY_MESSAGE.CHANNEL_ID.eq(channel.getIdLong()))
            .fetchAny());
    }

    /**
     * Deletes the sticky of this channel from the database.
     *
     * @param database the database to delete the sticky from
     * @param channel the channel to delete the sticky of
     */
    public static void deleteSticky(Database database, Channel channel) {
        database.write(context -> context.deleteFrom(STICKY_MESSAGE)
            .where(STICKY_MESSAGE.CHANNEL_ID.eq(channel.getIdLong()))
            .execute());
    }
}
