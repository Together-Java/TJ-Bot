package org.togetherjava.tjbot.features.reminder;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.jda.JdaTester;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.togetherjava.tjbot.db.generated.tables.PendingReminders.PENDING_REMINDERS;

final class RemindRoutineTest {
    private Routine routine;
    private JdaTester jdaTester;
    private RawReminderTestHelper rawReminders;

    @BeforeEach
    void setUp() {
        Database database = Database.createMemoryDatabase(PENDING_REMINDERS);
        routine = new RemindRoutine(database);
        jdaTester = new JdaTester();
        rawReminders = new RawReminderTestHelper(database, jdaTester);
    }

    private void triggerRoutine() {
        routine.runRoutine(jdaTester.getJdaMock());
    }

    private static MessageEmbed getLastMessageFrom(MessageChannel channel) {
        ArgumentCaptor<MessageEmbed> responseCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(channel).sendMessageEmbeds(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    private Member createAndSetupUnknownMember() {
        int unknownMemberId = 2;

        Member member = jdaTester.createMemberSpy(unknownMemberId);

        RestAction<User> unknownMemberAction = jdaTester.createFailedActionMock(
                jdaTester.createErrorResponseException(ErrorResponse.UNKNOWN_USER));
        when(jdaTester.getJdaMock().retrieveUserById(unknownMemberId))
            .thenReturn(unknownMemberAction);

        RestAction<PrivateChannel> unknownPrivateChannelAction = jdaTester.createFailedActionMock(
                jdaTester.createErrorResponseException(ErrorResponse.UNKNOWN_USER));
        when(jdaTester.getJdaMock().openPrivateChannelById(anyLong()))
            .thenReturn(unknownPrivateChannelAction);
        when(jdaTester.getJdaMock().openPrivateChannelById(anyString()))
            .thenReturn(unknownPrivateChannelAction);

        return member;
    }

    private TextChannel createAndSetupUnknownChannel() {
        long unknownChannelId = 2;

        TextChannel channel = jdaTester.createTextChannelSpy(unknownChannelId);
        when(jdaTester.getJdaMock()
            .getChannelById(ArgumentMatchers.<Class<MessageChannel>>any(), eq(unknownChannelId)))
                .thenReturn(null);

        return channel;
    }

    @Test
    @DisplayName("Sends out a pending reminder to a guild channel, the base case")
    void sendsPendingReminderChannelFoundAuthorFound() {
        // GIVEN a pending reminder
        Instant remindAt = Instant.now();
        String reminderContent = "foo";
        Member author = jdaTester.getMemberSpy();
        rawReminders.insertReminder("foo", remindAt, author);

        // WHEN running the routine
        triggerRoutine();

        // THEN the reminder is sent out and deleted from the database
        assertTrue(rawReminders.readReminders().isEmpty());

        MessageEmbed lastMessage = getLastMessageFrom(jdaTester.getTextChannelSpy());
        assertEquals(reminderContent, lastMessage.getDescription());
        assertSimilar(remindAt, lastMessage.getTimestamp().toInstant());
        assertEquals(author.getUser().getAsTag(), lastMessage.getAuthor().getName());
    }

    @Test
    @DisplayName("Sends out a pending reminder to a guild channel, even if the author could not be retrieved anymore")
    void sendsPendingReminderChannelFoundAuthorNotFound() {
        // GIVEN a pending reminder from an unknown user
        Instant remindAt = Instant.now();
        String reminderContent = "foo";
        Member unknownAuthor = createAndSetupUnknownMember();
        rawReminders.insertReminder("foo", remindAt, unknownAuthor);

        // WHEN running the routine
        triggerRoutine();

        // THEN the reminder is sent out and deleted from the database
        assertTrue(rawReminders.readReminders().isEmpty());

        MessageEmbed lastMessage = getLastMessageFrom(jdaTester.getTextChannelSpy());
        assertEquals(reminderContent, lastMessage.getDescription());
        assertSimilar(remindAt, lastMessage.getTimestamp().toInstant());
        assertEquals("Unknown user", lastMessage.getAuthor().getName());
    }

    @Test
    @DisplayName("Sends out a pending reminder via DM, even if the channel could not be retrieved anymore")
    void sendsPendingReminderChannelNotFoundAuthorFound() {
        // GIVEN a pending reminder from an unknown channel
        Instant remindAt = Instant.now();
        String reminderContent = "foo";
        Member author = jdaTester.getMemberSpy();
        TextChannel unknownChannel = createAndSetupUnknownChannel();
        rawReminders.insertReminder("foo", remindAt, author, unknownChannel);

        // WHEN running the routine
        triggerRoutine();

        // THEN the reminder is sent out and deleted from the database
        assertTrue(rawReminders.readReminders().isEmpty());

        MessageEmbed lastMessage = getLastMessageFrom(jdaTester.getPrivateChannelSpy());
        assertEquals(reminderContent, lastMessage.getDescription());
        assertSimilar(remindAt, lastMessage.getTimestamp().toInstant());
        assertEquals(author.getUser().getAsTag(), lastMessage.getAuthor().getName());
    }

    @Test
    @DisplayName("Skips a pending reminder if sending it out resulted in an error")
    void skipPendingReminderOnErrorChannelNotFoundAuthorNotFound() {
        // GIVEN a pending reminder and from an unknown channel and author
        Instant remindAt = Instant.now();
        String reminderContent = "foo";
        Member unknownAuthor = createAndSetupUnknownMember();
        TextChannel unknownChannel = createAndSetupUnknownChannel();
        rawReminders.insertReminder("foo", remindAt, unknownAuthor, unknownChannel);

        // WHEN running the routine
        triggerRoutine();

        // THEN the reminder is skipped and deleted from the database
        assertTrue(rawReminders.readReminders().isEmpty());
    }

    @Test
    @DisplayName("A reminder that is not pending yet, is not send out")
    void reminderIsNotSendIfNotPending() {
        // GIVEN a reminder that is not pending yet
        Instant remindAt = Instant.now().plus(1, ChronoUnit.HOURS);
        String reminderContent = "foo";
        rawReminders.insertReminder("foo", remindAt);

        // WHEN running the routine
        triggerRoutine();

        // THEN the reminder is not send yet and still in the database
        assertEquals(1, rawReminders.readReminders().size());
        verify(jdaTester.getTextChannelSpy(), never()).sendMessageEmbeds(any(MessageEmbed.class));
    }

    private static void assertSimilar(Instant expected, Instant actual) {
        // NOTE For some reason, the instant ends up in the database slightly wrong already (about
        // half a second), seems to be an issue with jOOQ
        assertEquals(expected.toEpochMilli(), actual.toEpochMilli(), TimeUnit.SECONDS.toMillis(1));
    }
}
