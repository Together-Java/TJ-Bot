package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.features.componentids.Lifespan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class SlashCommandAdapterTest {
    private static final String NAME = "foo";
    private static final String PREFIXED_NAME =
            UserInteractionType.SLASH_COMMAND.getPrefix() + NAME;
    private static final String DESCRIPTION = "Foo command";
    private static final CommandVisibility VISIBILITY = CommandVisibility.GUILD;
    private static final int UNIQUE_ID_ITERATIONS = 20;

    static SlashCommandAdapter createAdapter() {
        // noinspection AnonymousInnerClass
        return new SlashCommandAdapter(NAME, DESCRIPTION, VISIBILITY) {
            @Override
            public void onSlashCommand(SlashCommandInteractionEvent event) {
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
        SlashCommandData data = adapter.getData();
        assertEquals(NAME, data.getName(),
                "adapters name is inconsistent with the base data object");
        assertEquals(DESCRIPTION, data.getDescription(),
                "adapters description is inconsistent with the base data object");

        // Check that the method does not return a new data instance and does not mess with it
        String otherName = NAME + "-bar";
        String otherDescription = DESCRIPTION + "-bar";
        data.setName(otherName).setDescription(otherDescription);
        SlashCommandData otherData = adapter.getData();

        assertSame(data, otherData, "adapter changed the data object");
        assertEquals(otherName, otherData.getName(), "name changes did not carry over");
        assertEquals(otherDescription, otherData.getDescription(),
                "description changes did not carry over");
    }

    @Test
    void generateComponentId() {
        // Test that the adapter uses the given generator
        SlashCommandAdapter adapter = createAdapter();
        adapter.acceptComponentIdGenerator((componentId, lifespan) -> "%s;%s;%s"
            .formatted(componentId.userInteractorName(), componentId.elements().size(), lifespan));

        // No lifespan given
        String[] elements = {"foo", "bar", "baz"};
        String[] componentIdText = adapter.generateComponentId(elements).split(";");
        assertEquals(3, componentIdText.length);
        assertEquals(PREFIXED_NAME, componentIdText[0]);
        assertEquals(Integer.toString(elements.length), componentIdText[1]);
        assertEquals(Lifespan.REGULAR.toString(), componentIdText[2]);

        // Explicit lifespan
        for (Lifespan lifespan : Lifespan.values()) {
            componentIdText = adapter.generateComponentId(lifespan, elements).split(";");
            assertEquals(3, componentIdText.length);
            assertEquals(PREFIXED_NAME, componentIdText[0]);
            assertEquals(Integer.toString(elements.length), componentIdText[1]);
            assertEquals(lifespan.toString(), componentIdText[2]);
        }
    }
}
