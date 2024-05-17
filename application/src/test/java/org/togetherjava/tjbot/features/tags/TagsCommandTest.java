package org.togetherjava.tjbot.features.tags;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.jda.JdaTester;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

final class TagsCommandTest {
    private TagSystem system;
    private JdaTester jdaTester;
    private SlashCommand command;

    @Nullable
    private static String getResponseDescription(SlashCommandInteractionEvent event) {
        ArgumentCaptor<MessageEmbed> responseCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(responseCaptor.capture());
        return responseCaptor.getValue().getDescription();
    }

    private SlashCommandInteractionEvent triggerSlashCommand() {
        SlashCommandInteractionEvent event =
                jdaTester.createSlashCommandInteractionEvent(command).build();
        command.onSlashCommand(event);
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
}
