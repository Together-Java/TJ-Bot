package org.togetherjava.tjbot.commands.system;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.commands.BotCommand;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReloadCommandTest {
    @Test
    void ReloadCommand() {
        @SuppressWarnings({"AnonymousInnerClassWithTooManyMethods", "AnonymousInnerClass",
                "AnonymousInnerClassMayBeStatic"})
        BotCommandProvider slashCommandProvider = new BotCommandProvider() {
            @Override
            public @NotNull Collection<BotCommand> getBotCommands() {
                return List.of();
            }

            @Override
            public @NotNull Optional<BotCommand> getBotCommand(@NotNull String name) {
                return Optional.empty();
            }
        };

        // Adjust this test if the command is supposed to be renamed; make sure to update it in all
        // places.
        assertEquals("reload", new ReloadCommand(slashCommandProvider).getName(),
                "expected name of reload command to be 'reload', a name change can have severe consequences if not all sites that rely on this are updated.");
    }
}
