package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

final class TagsCommandTest {
    private TagSystem system;
    private JdaTester jdaTester;
    private SlashCommand command;

    private static @Nullable String getResponseDescription(
            @NotNull SlashCommandInteractionEvent event) {
        ArgumentCaptor<MessageEmbed> responseCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(responseCaptor.capture());
        return responseCaptor.getValue().getDescription();
    }

    private @NotNull SlashCommandInteractionEvent triggerSlashCommand() {
        SlashCommandInteractionEvent event =
                jdaTester.createSlashCommandInteractionEvent(command).build();
        command.onSlashCommand(event);
        return event;
    }

    private @NotNull ButtonInteractionEvent triggerButtonClick(@NotNull Member userWhoClicked,
            long idOfAuthor) {
        ButtonInteractionEvent event = jdaTester.createButtonInteractionEvent()
            .setUserWhoClicked(userWhoClicked)
            .setActionRows(ActionRow.of(TagSystem.createDeleteButton("foo")))
            .buildWithSingleButton();
        command.onButtonClick(event, List.of(Long.toString(idOfAuthor)));
        return event;
    }

    @BeforeEach
    void setUp() {
        system = spy(new TagSystem(Database.createMemoryDatabase(Tags.TAGS)));
        jdaTester = new JdaTester();
        command = jdaTester.spySlashCommand(new TagsCommand(system));
    }

    @Test
    @DisplayName("The list of tags is empty if there are no tags registered")
    void noResponseForEmptySystem() {
        // GIVEN a tag system without any tags
        // WHEN using '/tags'
        SlashCommandInteractionEvent event = triggerSlashCommand();

        // THEN the response has no description
        assertNull(getResponseDescription(event));
    }

    @Test
    @DisplayName("The list of tags shows a single element if there is one tag registered")
    void singleElementListForOneTag() {
        // GIVEN a tag system with the 'first' tag
        system.putTag("first", "foo");

        // WHEN using '/tags'
        SlashCommandInteractionEvent event = triggerSlashCommand();

        // THEN the response consists of the single element
        assertEquals("• first", getResponseDescription(event));
    }

    @Test
    @DisplayName("The list of tags shows multiply elements if there are multiple tags registered")
    void multipleElementListForMultipleTag() {
        // GIVEN a tag system with some tags
        system.putTag("first", "foo");
        system.putTag("second", "bar");
        system.putTag("third", "baz");

        // WHEN using '/tags'
        SlashCommandInteractionEvent event = triggerSlashCommand();

        // THEN the response contains all tags
        String expectedDescription = """
                • first
                • second
                • third""";
        assertEquals(expectedDescription, getResponseDescription(event));
    }

    @Test
    @DisplayName("The list of tags can be deleted by the original author")
    void authorCanDeleteList() {
        // GIVEN a '/tags' message send by an author
        long idOfAuthor = 1;
        Member messageAuthor = jdaTester.createMemberSpy(idOfAuthor);

        // WHEN the original author clicks the delete button
        ButtonInteractionEvent event = triggerButtonClick(messageAuthor, idOfAuthor);

        // THEN the '/tags' message is deleted
        verify(event.getMessage()).delete();
    }

    @Test
    @DisplayName("The list of tags can be deleted by a moderator")
    void moderatorCanDeleteList() {
        // GIVEN a '/tags' message send by an author and a moderator
        long idOfAuthor = 1;
        Member moderator = jdaTester.createMemberSpy(2);
        doReturn(true).when(moderator).hasPermission(any(Permission.class));

        // WHEN the moderator clicks the delete button
        ButtonInteractionEvent event = triggerButtonClick(moderator, idOfAuthor);

        // THEN the '/tags' message is deleted
        verify(event.getMessage()).delete();
    }

    @Test
    @DisplayName("The list of tags can not deleted by other users")
    void othersCanNotDeleteList() {
        // GIVEN a '/tags' message send by an author and another user
        long idOfAuthor = 1;
        Member otherUser = jdaTester.createMemberSpy(3);
        doReturn(false).when(otherUser).hasPermission(any(Permission.class));

        // WHEN the other clicks the delete button
        ButtonInteractionEvent event = triggerButtonClick(otherUser, idOfAuthor);

        // THEN the '/tags' message is not deleted
        verify(event.getMessage(), never()).delete();
        verify(event).reply(anyString());
    }
}
