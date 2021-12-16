package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.modmail.ModMailUtil;
import org.togetherjava.tjbot.config.Config;

import java.util.Objects;

public class ReloadModMailCommand extends SlashCommandAdapter {

    private final JDA jda;
    private static final Config config = Config.getInstance();

    /**
     * Creates an instance of ReloadMod command.
     *
     * @param jda the JDA instance to find the guild.
     */
    public ReloadModMailCommand(@NotNull JDA jda) {
        super("reloadmod", "Reloads the list of moderators in the modmail selection menu",
                SlashCommandVisibility.GUILD);

        this.jda = Objects.requireNonNull(jda);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Guild guild = Objects.requireNonNull(jda.getGuildById(config.getGuildId()),
                "A Guild is required to use this command. Perhaps the bot isn't on the guild yet");

        if (ModMailUtil.doesUserHaveModRole(event.getMember(), guild)) {
            event.reply("Only moderators can use this command.").setEphemeral(true).queue();
            return;
        }

        ModMailUtil.loadMenuOptions(guild);
        event.reply("List of moderators has now been reloaded.").setEphemeral(true).queue();
    }

}
