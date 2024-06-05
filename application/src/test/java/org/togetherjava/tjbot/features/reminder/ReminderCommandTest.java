package org.togetherjava.tjbot.features.reminder;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.jda.JdaTester;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.togetherjava.tjbot.db.generated.tables.PendingReminders.PENDING_REMINDERS;

final class ReminderCommandTest {
    private SlashCommand command;
    private JdaTester jdaTester;
    private RawReminderTestHelper rawReminders;

    @BeforeEach
    void setUp() {
        Database database = Database.createMemoryDatabase(PENDING_REMINDERS);
        command = new ReminderCommand(database);
        jdaTester = new JdaTester();
        rawReminders = new RawReminderTestHelper(database, jdaTester);
    }

    private SlashCommandInteractionEvent triggerSlashCommand(int timeAmount, String timeUnit,
            String content) {
        return triggerSlashCommand(timeAmount, timeUnit, content, jdaTester.getMemberSpy());
    }

    private SlashCommandInteractionEvent triggerSlashCommand(int timeAmount, String timeUnit,
            String content, Member author) {
        SlashCommandInteractionEvent event = jdaTester.createSlashCommandInteractionEvent(command)
            .setSubcommand(ReminderCommand.CREATE_SUBCOMMAND)
            .setOption(ReminderCommand.TIME_AMOUNT_OPTION, timeAmount)
            .setOption(ReminderCommand.TIME_UNIT_OPTION, timeUnit)
            .setOption(ReminderCommand.CONTENT_OPTION, content)
            .setUserWhoTriggered(author)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    @Test
    @DisplayName("Throws an exception if the time unit is not supported, i.e. not part of the actual choice dialog")
    void throwsWhenGivenUnsupportedUnit() {
        // GIVEN
        // WHEN triggering /remind with the unsupported time unit 'nanoseconds'
        Executable triggerRemind = () -> triggerSlashCommand(10, "nanoseconds", "foo");

        // THEN command throws, no reminder was created
        Assertions.assertThrows(IllegalArgumentException.class, triggerRemind);
        assertTrue(rawReminders.readReminders().isEmpty());
    }

    @Test
    @DisplayName("Rejects a reminder time that is set too far in the future and responds accordingly")
    void doesNotSupportDatesTooFarInFuture() {
        // GIVEN
        // WHEN triggering /remind too far in the future
        SlashCommandInteractionEvent event = triggerSlashCommand(10, "years", "foo");

        // THEN rejects and responds accordingly, no reminder was created
        verify(event).reply(startsWith("The reminder is set too far in the future"));
        assertTrue(rawReminders.readReminders().isEmpty());
    }

    @Test
    @DisplayName("Rejects a reminder if a user has too many reminders still pending")
    void userIsLimitedIfTooManyPendingReminders() {
        // GIVEN a user with too many reminders still pending
        Instant remindAt = Instant.now().plus(100, ChronoUnit.DAYS);
        for (int i = 0; i < ReminderCommand.MAX_PENDING_REMINDERS_PER_USER; i++) {
            rawReminders.insertReminder("foo " + i, remindAt);
        }

        // WHEN triggering another reminder
        SlashCommandInteractionEvent event = triggerSlashCommand(5, "minutes", "foo");

        // THEN rejects and responds accordingly, no new reminder was created
        verify(event)
            .reply(startsWith("You have reached the maximum amount of pending reminders per user"));
        assertEquals(ReminderCommand.MAX_PENDING_REMINDERS_PER_USER,
                rawReminders.readReminders().size());
    }

    @Test
    @DisplayName("Does not limit a user if another user has too many reminders still pending, i.e. the limit is per user")
    void userIsNotLimitedIfOtherUserHasTooManyPendingReminders() {
        // GIVEN a user with too many reminders still pending,
        // and a second user with no reminders yet
        Member firstUser = jdaTester.createMemberSpy(1);
        Instant remindAt = Instant.now().plus(100, ChronoUnit.DAYS);
        for (int i = 0; i < ReminderCommand.MAX_PENDING_REMINDERS_PER_USER; i++) {
            rawReminders.insertReminder("foo " + i, remindAt, firstUser);
        }

        Member secondUser = jdaTester.createMemberSpy(2);

        // WHEN the second user triggers another reminder
        SlashCommandInteractionEvent event = triggerSlashCommand(5, "minutes", "foo", secondUser);

        // THEN accepts the reminder and responds accordingly
        verify(event).reply("Will remind you about 'foo' in 5 minutes.");

        List<String> remindersOfSecondUser = rawReminders.readReminders(secondUser);
        assertEquals(1, remindersOfSecondUser.size());
        assertEquals("foo", remindersOfSecondUser.getFirst());
    }

    @Test
    @DisplayName("The command can create a reminder, the regular base case")
    void canCreateReminders() {
        // GIVEN
        // WHEN triggering the /remind command
        SlashCommandInteractionEvent event = triggerSlashCommand(5, "minutes", "foo");

        // THEN accepts the reminder and responds accordingly
        verify(event).reply("Will remind you about 'foo' in 5 minutes.");

        List<String> pendingReminders = rawReminders.readReminders();
        assertEquals(1, pendingReminders.size());
        assertEquals("foo", pendingReminders.getFirst());
    }
}
