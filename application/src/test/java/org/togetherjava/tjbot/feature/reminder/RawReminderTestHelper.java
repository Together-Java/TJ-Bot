package org.togetherjava.tjbot.feature.reminder;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.Tables;
import org.togetherjava.tjbot.db.generated.tables.records.PendingRemindersRecord;
import org.togetherjava.tjbot.jda.JdaTester;

import java.time.Instant;
import java.util.List;

import static org.togetherjava.tjbot.db.generated.tables.PendingReminders.PENDING_REMINDERS;

final class RawReminderTestHelper {
    private Database database;
    private JdaTester jdaTester;

    RawReminderTestHelper(@NotNull Database database, @NotNull JdaTester jdaTester) {
        this.database = database;
        this.jdaTester = jdaTester;
    }

    void insertReminder(@NotNull String content, @NotNull Instant remindAt) {
        insertReminder(content, remindAt, jdaTester.getMemberSpy(), jdaTester.getTextChannelSpy());
    }

    void insertReminder(@NotNull String content, @NotNull Instant remindAt,
            @NotNull Member author) {
        insertReminder(content, remindAt, author, jdaTester.getTextChannelSpy());
    }

    void insertReminder(@NotNull String content, @NotNull Instant remindAt, @NotNull Member author,
            @NotNull TextChannel channel) {
        long channelId = channel.getIdLong();
        long guildId = channel.getGuild().getIdLong();
        long authorId = author.getIdLong();

        database.write(context -> context.newRecord(Tables.PENDING_REMINDERS)
            .setCreatedAt(Instant.now())
            .setGuildId(guildId)
            .setChannelId(channelId)
            .setAuthorId(authorId)
            .setRemindAt(remindAt)
            .setContent(content)
            .insert());
    }

    @NotNull
    List<String> readReminders() {
        return readReminders(jdaTester.getMemberSpy());
    }

    @NotNull
    List<String> readReminders(@NotNull Member author) {
        long guildId = jdaTester.getTextChannelSpy().getGuild().getIdLong();
        long authorId = author.getIdLong();

        return database.read(context -> context.selectFrom(PENDING_REMINDERS)
            .where(PENDING_REMINDERS.AUTHOR_ID.eq(authorId)
                .and(PENDING_REMINDERS.GUILD_ID.eq(guildId)))
            .stream()
            .map(PendingRemindersRecord::getContent)
            .toList());
    }
}
