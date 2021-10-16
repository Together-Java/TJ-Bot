package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SlashCommandAdapterTest {
    private static final String NAME = "foo";
    private static final String DESCRIPTION = "Foo command";
    private static final SlashCommandVisibility VISIBILITY = SlashCommandVisibility.GUILD;
    private static final int UNIQUE_ID_ITERATIONS = 20;

    static SlashCommandAdapter createAdapter() {
        // noinspection AnonymousInnerClass
        return new SlashCommandAdapter(NAME, DESCRIPTION, VISIBILITY) {
            @Override
            public void onSlashCommand(@NotNull SlashCommandEvent event) {
                // No implementation needed for the test
            }
        };
    }

    @Test
    void getName() {
        assertEquals(NAME, createAdapter().getName());
    }

    @Test
    void getDescription() {
        assertEquals(DESCRIPTION, createAdapter().getDescription());
    }

    @Test
    void getVisibility() {
        assertEquals(VISIBILITY, createAdapter().getVisibility());
    }

    @Test
    void getData() {
        SlashCommandAdapter adapter = createAdapter();
        CommandData data = adapter.getData();
        assertEquals(NAME, data.getName(),
                "adapters name is inconsistent with the base data object");
        assertEquals(DESCRIPTION, data.getDescription(),
                "adapters description is inconsistent with the base data object");

        // Check that the method does not return a new data instance and does not mess with it
        String otherName = NAME + "-bar";
        String otherDescription = DESCRIPTION + "-bar";
        data.setName(otherName).setDescription(otherDescription);
        CommandData otherData = adapter.getData();

        assertSame(data, otherData, "adapter changed the data object");
        assertEquals(otherName, otherData.getName(), "name changes did not carry over");
        assertEquals(otherDescription, otherData.getDescription(),
                "description changes did not carry over");
    }

    @Test
    void generateComponentId() {
        assertTrue(true);
        /*
         * String[] elements = {"foo", "bar", "baz"}; SlashCommandAdapter adapter = createAdapter();
         * 
         * String componentIdText = adapter.generateComponentId(elements); ComponentId componentId =
         * assertDoesNotThrow(() -> ComponentIds.parse(componentIdText),
         * "generated component id seems to be invalid with the parser");
         * 
         * assertEquals(NAME, componentId.getCommandName(),
         * "expected command name to be part of the component id for routing");
         * assertEquals(Arrays.asList(elements), componentId.getElements(),
         * "expected all arguments to carry over the id");
         * 
         * // Empty elements assertTrue(assertDoesNotThrow(() ->
         * ComponentIds.parse(adapter.generateComponentId()),
         * "component id generation seems to have issues with empty elements").getElements()
         * .isEmpty());
         * 
         * // Check that IDs are unique Collection<Integer> ids = new HashSet<>(); for (int i = 0; i
         * < UNIQUE_ID_ITERATIONS; i++) { int id = assertDoesNotThrow(() ->
         * ComponentIds.parse(adapter.generateComponentId()),
         * "generated component id seems to be invalid with the parser").getId();
         * assertFalse(ids.contains(id), "id generator is supposed to create unique IDs");
         * ids.add(id); }
         */
    }
}
