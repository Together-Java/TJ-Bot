package org.togetherjava.tjbot.features.tags;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.features.moderation.audit.ModAuditLogWriter;
import org.togetherjava.tjbot.jda.JdaTester;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TagManageCommandTest {
    private TagSystem system;
    private JdaTester jdaTester;
    private SlashCommand command;
    private Member moderator;
    private ModAuditLogWriter modAuditLogWriter;

    private static MessageEmbed getResponse(SlashCommandInteractionEvent event) {
        ArgumentCaptor<MessageEmbed> responseCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    @BeforeEach
    void setUp() {
        Config config = mock(Config.class);
        String moderatorRoleName = "Moderator";
        when(config.getTagManageRolePattern()).thenReturn(moderatorRoleName);
        modAuditLogWriter = mock(ModAuditLogWriter.class);

        Database database = Database.createMemoryDatabase(Tags.TAGS);
        system = spy(new TagSystem(database));
        jdaTester = new JdaTester();
        command = new TagManageCommand(system, modAuditLogWriter);

        moderator = jdaTester.createMemberSpy(1);
        Role moderatorRole = mock(Role.class);
        doReturn(true).when(moderator).hasPermission(any(Permission.class));
        doReturn(List.of(moderatorRole)).when(moderator).getRoles();
        when(moderatorRole.getName()).thenReturn(moderatorRoleName);
    }

    private SlashCommandInteractionEvent triggerRawCommand(String tagId) {
        return triggerRawCommandWithUser(tagId, moderator);
    }

    private SlashCommandInteractionEvent triggerRawCommandWithUser(String tagId, Member user) {
        SlashCommandInteractionEvent event = jdaTester.createSlashCommandInteractionEvent(command)
            .setSubcommand(TagManageCommand.Subcommand.RAW.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setUserWhoTriggered(user)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private SlashCommandInteractionEvent triggerCreateCommand(String tagId, String content) {
        return triggerTagContentCommand(TagManageCommand.Subcommand.CREATE, tagId, content);
    }

    private SlashCommandInteractionEvent triggerEditCommand(String tagId, String content) {
        return triggerTagContentCommand(TagManageCommand.Subcommand.EDIT, tagId, content);
    }

    private SlashCommandInteractionEvent triggerTagContentCommand(
            TagManageCommand.Subcommand subcommand, String tagId, String content) {
        SlashCommandInteractionEvent event = jdaTester.createSlashCommandInteractionEvent(command)
            .setSubcommand(subcommand.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setOption(TagManageCommand.CONTENT_OPTION, content)
            .setUserWhoTriggered(moderator)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private SlashCommandInteractionEvent triggerCreateWithMessageCommand(String tagId,
            String messageId) {
        return triggerTagMessageCommand(TagManageCommand.Subcommand.CREATE_WITH_MESSAGE, tagId,
                messageId);
    }

    private SlashCommandInteractionEvent triggerEditWithMessageCommand(String tagId,
            String messageId) {
        return triggerTagMessageCommand(TagManageCommand.Subcommand.EDIT_WITH_MESSAGE, tagId,
                messageId);
    }

    private SlashCommandInteractionEvent triggerTagMessageCommand(
            TagManageCommand.Subcommand subcommand, String tagId, String messageId) {
        SlashCommandInteractionEvent event = jdaTester.createSlashCommandInteractionEvent(command)
            .setSubcommand(subcommand.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setOption(TagManageCommand.MESSAGE_ID_OPTION, messageId)
            .setUserWhoTriggered(moderator)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private SlashCommandInteractionEvent triggerDeleteCommand(String tagId) {
        SlashCommandInteractionEvent event = jdaTester.createSlashCommandInteractionEvent(command)
            .setSubcommand(TagManageCommand.Subcommand.DELETE.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setUserWhoTriggered(moderator)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private void postMessage(String content, String id) {
        Message message = jdaTester.clientMessageToReceivedMessageMock(
                new MessageCreateBuilder().setContent(content).build());
        doReturn(jdaTester.createSucceededActionMock(message)).when(jdaTester.getTextChannelSpy())
            .retrieveMessageById(id);
    }

    private void failOnRetrieveMessage(String messageId, Throwable failure) {
        doReturn(jdaTester.createFailedActionMock(failure)).when(jdaTester.getTextChannelSpy())
            .retrieveMessageById(messageId);
    }

    @Test
    @DisplayName("'/tag-manage raw' can not be used on unknown tags")
    void rawTagCanNotFindUnknownTag() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage raw id:unknown'
        SlashCommandInteractionEvent event = triggerRawCommand("unknown");

        // THEN the command can not find the tag and responds accordingly
        verify(event).reply(startsWith("Could not find any tag"));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage raw' shows the raw content of a known tag")
    void rawTagShowsContentIfFound() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "bar");

        // WHEN using '/tag-manage raw id:foo'
        triggerRawCommand("foo");

        // THEN the command responds with its content as an attachment
        verify(jdaTester.getReplyActionMock())
            .addFiles(argThat(jdaTester.createAttachmentHasContentMatcher("bar")));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage create' fails if the tag already exists")
    void createTagThatAlreadyExistsFails() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");

        // WHEN using '/tag-manage create id:foo content:new'
        SlashCommandInteractionEvent event = triggerCreateCommand("foo", "new");

        // THEN the command fails and responds accordingly, the tag is still there and unchanged
        verify(event).reply("The tag with id 'foo' already exists.");
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage create' works if the tag is new")
    void createNewTagWorks() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage create id:foo content:bar'
        SlashCommandInteractionEvent event = triggerCreateCommand("foo", "bar");

        // THEN the command succeeds and the system contains the tag
        assertEquals("Success", getResponse(event).getTitle());
        assertTrue(system.hasTag("foo"));
        assertEquals("bar", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter).write("Tag-Manage Create", "created tag **foo**", event.getUser(),
                event.getTimeCreated(), event.getGuild(),
                new ModAuditLogWriter.Attachment("content.md", "bar"));
    }

    @Test
    @DisplayName("'/tag-manage edit' fails if the tag is unknown")
    void editUnknownTagFails() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage edit id:foo content:new'
        SlashCommandInteractionEvent event = triggerEditCommand("foo", "new");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply(startsWith("Could not find any tag with id"));
        assertFalse(system.hasTag("foo"));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage edit' works if the tag is known")
    void editExistingTagWorks() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");

        // WHEN using '/tag-manage edit id:foo content:new'
        SlashCommandInteractionEvent event = triggerEditCommand("foo", "new");

        // THEN the command succeeds and the content of the tag was changed
        assertEquals("Success", getResponse(event).getTitle());
        assertEquals("new", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter).write("Tag-Manage Edit", "edited tag **foo**", event.getUser(),
                event.getTimeCreated(), event.getGuild(),
                new ModAuditLogWriter.Attachment("new_content.md", "new"),
                new ModAuditLogWriter.Attachment("previous_content.md", "old"));
    }

    @Test
    @DisplayName("'/tag-manage delete' fails if the tag is unknown")
    void deleteUnknownTagFails() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage delete id:foo'
        SlashCommandInteractionEvent event = triggerDeleteCommand("foo");

        // THEN the command fails and responds accordingly
        verify(event).reply(startsWith("Could not find any tag with id"));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage delete' works if the tag is known")
    void deleteExistingTagWorks() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "bar");

        // WHEN using '/tag-manage delete id:foo'
        SlashCommandInteractionEvent event = triggerDeleteCommand("foo");

        // THEN the command succeeds and the tag was deleted
        assertEquals("Success", getResponse(event).getTitle());
        assertFalse(system.hasTag("foo"));
        verify(modAuditLogWriter).write("Tag-Manage Delete", "deleted tag **foo**", event.getUser(),
                event.getTimeCreated(), event.getGuild(),
                new ModAuditLogWriter.Attachment("previous_content.md", "bar"));
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if the given message id is in an invalid format")
    void createWithMessageFailsForInvalidMessageId() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage create-with-message id:foo message-id:bar'
        SlashCommandInteractionEvent event = triggerCreateWithMessageCommand("foo", "bar");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply("The given message id 'bar' is invalid, expected a number.");
        assertFalse(system.hasTag("foo"));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if the tag is known")
    void createWithMessageTagThatAlreadyExistsFails() {
        // GIVEN a tag system with the "foo" tag and a message with id and content
        system.putTag("foo", "old");
        postMessage("new", "1");

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandInteractionEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag is still there and unchanged
        verify(event).reply("The tag with id 'foo' already exists.");
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' works if the tag is new")
    void createWithMessageNewTagWorks() {
        // GIVEN a tag system without any tags and a message with id and content
        postMessage("bar", "1");

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandInteractionEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command succeeds and the system contains the tag
        assertEquals("Success", getResponse(event).getTitle());
        assertTrue(system.hasTag("foo"));
        assertEquals("bar", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter).write("Tag-Manage Create with message", "created tag **foo**",
                event.getUser(), event.getTimeCreated(), event.getGuild(),
                new ModAuditLogWriter.Attachment("content.md", "bar"));
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if the linked message is unknown")
    void createWithMessageUnknownMessageFails() {
        // GIVEN a tag system without any tags and an unknown message id
        failOnRetrieveMessage("1",
                jdaTester.createErrorResponseException(ErrorResponse.UNKNOWN_MESSAGE));

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandInteractionEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply("The message with id '1' does not exist.");
        assertFalse(system.hasTag("foo"));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if there is a generic error (such as a network failure)")
    void createWithMessageGenericErrorFails() {
        // GIVEN a tag system without any tags and a generic network failure
        failOnRetrieveMessage("1", new IOException("Generic network failure"));

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandInteractionEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply(startsWith("Something unexpected went wrong"));
        assertFalse(system.hasTag("foo"));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if the given message id is in an invalid format")
    void editWithMessageFailsForInvalidMessageId() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");

        // WHEN using '/tag-manage edit-with-message id:foo message-id:new'
        SlashCommandInteractionEvent event = triggerEditWithMessageCommand("foo", "bar");

        // THEN the command fails and responds accordingly, the tags content was not changed
        verify(event).reply("The given message id 'bar' is invalid, expected a number.");
        assertEquals("old", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if the tag is unknown")
    void editWithMessageUnknownTagFails() {
        // GIVEN a tag system without any tags
        postMessage("bar", "1");

        // WHEN using '/tag-manage edit-with-message id:foo message-id:new'
        SlashCommandInteractionEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply(startsWith("Could not find any tag with id"));
        assertFalse(system.hasTag("foo"));
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' works if the tag is known")
    void editWithMessageExistingTagWorks() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");
        postMessage("new", "1");

        // WHEN using '/tag-manage edit-with-message id:foo message-id:1'
        SlashCommandInteractionEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command succeeds and the content of the tag was changed
        assertEquals("Success", getResponse(event).getTitle());
        assertEquals("new", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter).write("Tag-Manage Edit with message", "edited tag **foo**",
                event.getUser(), event.getTimeCreated(), event.getGuild(),
                new ModAuditLogWriter.Attachment("new_content.md", "new"),
                new ModAuditLogWriter.Attachment("previous_content.md", "old"));
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if the linked message is unknown")
    void editWithMessageUnknownMessageFails() {
        // GIVEN a tag system with the "foo" tag and an unknown message id
        system.putTag("foo", "old");
        failOnRetrieveMessage("1",
                jdaTester.createErrorResponseException(ErrorResponse.UNKNOWN_MESSAGE));

        // WHEN using '/tag-manage edit-with-message id:foo message-id:1'
        SlashCommandInteractionEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag has not changed
        verify(event).reply("The message with id '1' does not exist.");
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if there is a generic error (such as a network failure)")
    void editWithMessageGenericErrorFails() {
        // GIVEN a tag system with the "foo" tag and a generic network failure
        system.putTag("foo", "old");
        failOnRetrieveMessage("1", new IOException("Generic network failure"));

        // WHEN using '/tag-manage edit-with-message id:foo message-id:1'
        SlashCommandInteractionEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag has not changed
        verify(event).reply(startsWith("Something unexpected went wrong"));
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
        verify(modAuditLogWriter, never()).write(any(), any(), any(), any(), any(), any());
    }
}
