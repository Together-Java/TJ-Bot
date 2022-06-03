package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

/**
 * Implements the {@code /close} command to close question threads.
 * <p>
 * Can be used in active (non-archived) question threads. Will close, i.e. archive, the thread upon
 * use. Meant to be used once a question has been resolved.
 */
public final class CloseCommand extends SlashCommandAdapter {
    private final HelpSystemHelper helper;

    /**
     * Creates a new instance.
     *
     * @param helper the helper to use
     */
    public CloseCommand(@NotNull HelpSystemHelper helper) {
        super("close", "Close this question thread", SlashCommandVisibility.GUILD);

        this.helper = helper;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        if (!helper.handleIsHelpThread(event)) {
            return;
        }

        ThreadChannel helpThread = event.getThreadChannel();
        if (helpThread.isArchived()) {
            event.reply("This thread is already closed.").setEphemeral(true).queue();
            return;
        }

        MessageEmbed embed = new EmbedBuilder().setDescription("Closed the thread.")
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();

        event.replyEmbeds(embed).flatMap(any -> helpThread.getManager().setArchived(true)).queue();
    }
}
