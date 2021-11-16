package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

public class AuditCommand extends SlashCommandAdapter {
    /**
     * Creates a new adapter with the given data.
     */
    protected AuditCommand() {
        super("audit", "get the audit for the commands", SlashCommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.getChannel().sendMessage("In creation").queue();
    }
}
