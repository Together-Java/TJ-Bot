package org.togetherjava.tjbot.commands.modmail;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

public class ReloadModMailCommand extends SlashCommandAdapter {

    /**
     * Creates an instance of ReloadMod command.
     */
    protected ReloadModMailCommand() {
        super("reloadmod",
                "Reloads the list of moderators that users can choose to send modmail messages to",
                SlashCommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {

    }
}
