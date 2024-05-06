package org.togetherjava.tjbot.features.tags;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

final class TagSystemTest {
    private TagSystem system;
    private Database database;
    private JdaTester jdaTester;

    private void insertTagRaw(String id, String content) {
        database
            .write(context -> context.newRecord(Tags.TAGS).setId(id).setContent(content).insert());
    }

    private Optional<String> readTagRaw(String id) {
        return database.read(context -> context.selectFrom(Tags.TAGS)
            .where(Tags.TAGS.ID.eq(id))
            .fetchOptional(Tags.TAGS.CONTENT));
    }

    private int getAmountOfRecords() {
        return database.read(context -> context.fetchCount(Tags.TAGS));
    }

    @BeforeEach
    void setUp() {
        database = Database.createMemoryDatabase(Tags.TAGS);
        system = spy(new TagSystem(database));
        jdaTester = new JdaTester();
    }

    @Test
    void createDeleteButton() {
        assertEquals("foo", TagSystem.createDeleteButton("foo").getId());
        assertEquals("fooBarFooBar", TagSystem.createDeleteButton("fooBarFooBar").getId());
    }

    @Test
    void handleIsUnknownTag() {
        insertTagRaw("known", "foo");
        SlashCommandInteractionEvent event =
                jdaTester.createSlashCommandInteractionEvent(new TagCommand(system)).build();

        assertFalse(system.handleIsUnknownTag("known", event));
        verify(event, never()).reply(anyString());

        assertTrue(system.handleIsUnknownTag("unknown", event));
        verify(event).reply(anyString());
    }

    @Test
    void hasTag() {
        insertTagRaw("known", "foo");

        assertTrue(system.hasTag("known"));
        assertFalse(system.hasTag("unknown"));
    }

    @Test
    void deleteTag() {
        insertTagRaw("known", "foo");

        assertThrowsExactly(IllegalArgumentException.class, () -> system.deleteTag("unknown"));
        assertEquals(1, getAmountOfRecords());

        system.deleteTag("known");
        assertEquals(0, getAmountOfRecords());
    }

    @Test
    void putTag() {
        insertTagRaw("before", "foo");

        system.putTag("now", "bar");

        Optional<String> maybeContent = readTagRaw("now");
        assertTrue(maybeContent.isPresent());
        assertEquals("bar", maybeContent.orElseThrow());

        // Overwrite existing content
        system.putTag("before", "baz");
        maybeContent = readTagRaw("before");
        assertTrue(maybeContent.isPresent());
        assertEquals("baz", maybeContent.orElseThrow());
    }

    @Test
    void getTag() {
        insertTagRaw("known", "foo");

        assertTrue(system.getTag("unknown").isEmpty());

        Optional<String> maybeContent = system.getTag("known");
        assertTrue(maybeContent.isPresent());
        assertEquals("foo", maybeContent.orElseThrow());
    }

    @Test
    void getAllIds() {
        assertTrue(system.getAllIds().isEmpty());

        insertTagRaw("first", "foo");
        assertEquals(Set.of("first"), system.getAllIds());

        insertTagRaw("second", "bar");
        assertEquals(Set.of("first", "second"), system.getAllIds());

        insertTagRaw("third", "baz");
        assertEquals(Set.of("first", "second", "third"), system.getAllIds());
    }
}
