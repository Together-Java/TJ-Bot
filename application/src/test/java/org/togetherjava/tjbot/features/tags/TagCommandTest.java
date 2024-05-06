package org.togetherjava.tjbot.features.tags;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.jda.JdaTester;
import org.togetherjava.tjbot.jda.SlashCommandInteractionEventBuilder;

import javax.annotation.Nullable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

final class TagCommandTest {
    private TagSystem system;
    private JdaTester jdaTester;
    private SlashCommand command;

    @BeforeEach
    void setUp() {
        Database database = Database.createMemoryDatabase(Tags.TAGS);
        system = spy(new TagSystem(database));
        jdaTester = new JdaTester();
        command = new TagCommand(system);
    }

    private SlashCommandInteractionEvent triggerSlashCommand(String id,
            @Nullable Member userToReplyTo) {
        SlashCommandInteractionEventBuilder builder =
                jdaTester.createSlashCommandInteractionEvent(command)
                    .setOption(TagCommand.ID_OPTION, id);
        if (userToReplyTo != null) {
            builder.setOption(TagCommand.REPLY_TO_USER_OPTION, userToReplyTo);
        }

        SlashCommandInteractionEvent event = builder.build();
        command.onSlashCommand(event);
        return event;
    }

    @Test
    @DisplayName("Respond that the tag could not be found if the system has no tags registered yet")
    void canNotFindTagInEmptySystem() {
        // GIVEN a system without any tags registered
        // WHEN triggering the slash command '/tag id:first'
        SlashCommandInteractionEvent event = triggerSlashCommand("first", null);

        // THEN responds that the tag could not be found
        verify(event).reply("Could not find any tag with id 'first'.");
    }

    @Test
    @DisplayName("Respond that the tag could not be found but suggest a different tag instead, if the system has a different tag registered")
    void canNotFindTagSuggestDifferentTag() {
        // GIVEN a system with the tag "first" registered
        system.putTag("first", "foo");

        // WHEN triggering the slash command '/tag id:second'
        SlashCommandInteractionEvent event = triggerSlashCommand("second", null);

        // THEN responds that the tag could not be found and instead suggests using the other tag
        verify(event)
            .reply("Could not find any tag with id 'second', did you perhaps mean 'first'?");
    }

    @Test
    @DisplayName("Respond with the tags content if the tag could be found")
    void canFindTheTagAndRespondWithContent() {
        // GIVEN a system with the tag "first" registered
        system.putTag("first", "foo");

        // WHEN triggering the slash command '/tag id:first'
        SlashCommandInteractionEvent event = triggerSlashCommand("first", null);

        // THEN finds the tag and responds with its content
        verify(event).replyEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("Replies to given users and responds with the tags content if the tag could be found and a user is given")
    void canFindTagsAndRepliesToUser() {
        // GIVEN a system with the tag "first" registered and a user to reply to
        system.putTag("first", "foo");
        Member userToReplyTo = jdaTester.createMemberSpy(1);

        // WHEN triggering the slash command '/tag id:first reply-to:...' with that user
        SlashCommandInteractionEvent event = triggerSlashCommand("first", userToReplyTo);

        // THEN responds with the tags content and replies to the user
        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(jdaTester.getReplyActionMock()).setContent(userToReplyTo.getAsMention());
    }
}
