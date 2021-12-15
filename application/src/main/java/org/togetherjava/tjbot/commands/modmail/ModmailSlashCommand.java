package org.togetherjava.tjbot.commands.modmail;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ModmailSlashCommand extends SlashCommandAdapter {

    private static final String COMMAND_NAME = "modmail";
    private static final String TARGET_OPTION = "message";
    private static final Config config = Config.getInstance();

    public ModmailSlashCommand() {
        super(COMMAND_NAME,
                "sends a message to either a single moderator or on the mod_audit_log channel",
                SlashCommandVisibility.GLOBAL);

        getData().addOption(OptionType.STRING, TARGET_OPTION, "The message to send", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String memberId = event.getUser().getId();
        event.reply("""
                Select the moderator to send message to, or select "All Moderators" to send to
                the guild's mod audit channel.
                """)
            .addActionRow(SelectionMenu.create(generateComponentId(memberId))
                .addOptions(listOfModerators(event.getJDA()))
                .build())
            .setEphemeral(false)
            .queue();
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        // Ignore if another user clicked the button
        String userId = args.get(0);
        if (!userId.equals(Objects.requireNonNull(event.getMember()).getId())) {
            event.reply(
                    "Sorry, but only the user who triggered the command can interact with the menu.")
                .setEphemeral(true)
                .queue();
            return;
        }

        SelectionMenu selectionMenu = event.getSelectionMenu();
        SelectionMenu disabledMenu = selectionMenu.asDisabled();
        if (event.getValues().get(0).equals("all")) {
            event.reply("Message now sent to all mods").setEphemeral(true).queue();
            return;
        }
        event.reply("Message now sent to selected mods").setEphemeral(true).queue();

        event.getMessage().editMessageComponents(ActionRow.of(disabledMenu)).queue();
    }

    /**
     * Creates a list of options containing the moderators for use in the modmail slash command.
     *
     * @param jda
     * @return a list of options in a selection menu
     */
    private List<SelectOption> listOfModerators(JDA jda) {
        List<SelectOption> menuChoices = new ArrayList<>();

        Guild guild = jda.getGuildById(config.getGuildId());

        guild.findMembersWithRoles(guild.getRolesByName("moderator", true))
            .get()
            .stream()
            .forEach(mod -> menuChoices.add(SelectOption.of(mod.getEffectiveName(), mod.getId())));
        menuChoices.add(SelectOption.of("All Moderators", "all"));

        return menuChoices;
    }

}
