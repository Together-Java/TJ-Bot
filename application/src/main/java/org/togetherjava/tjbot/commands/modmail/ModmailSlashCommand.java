package org.togetherjava.tjbot.commands.modmail;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.free.FreeCommand;
import org.togetherjava.tjbot.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ModmailSlashCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ModmailSlashCommand.class);

    private static final String COMMAND_NAME = "modmail";
    private static final String TARGET_OPTION = "message";
    private static final Config config = Config.getInstance();

    private static final List<SelectOption> mods = new ArrayList<>();

    private final JDA jda;

    public ModmailSlashCommand(JDA jda) {
        super(COMMAND_NAME,
                "sends a message to either a single moderator or on the mod_audit_log channel",
                SlashCommandVisibility.GLOBAL);

        getData().addOption(OptionType.STRING, TARGET_OPTION, "The message to send", true);

        this.jda = jda;

        mods.add(SelectOption.of("All Moderators", "all"));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String memberId = event.getUser().getId();
        event.reply("""
                Select the moderator to send message to, or select "All Moderators" to send to
                the guild's mod audit channel.
                """)
            .addActionRow(SelectionMenu.create(generateComponentId(memberId))
                .addOptions(selectionMenuOptions())
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
     * <p/>
     * If this method has not yet been called prior to calling this method, it will call an
     * expensive query to discord, otherwise, it will return the previous result.
     *
     * @return a list of options containing the moderators name to choose from in a selection menu.
     */
    private List<SelectOption> selectionMenuOptions() {
        Guild guild = jda.getGuildById(config.getGuildId());

        // checks if method has been called before.
        if (mods.size() == 1) {
            guild.findMembersWithRoles(guild.getRolesByName("moderator", true))
                .get()
                .stream()
                .forEach(mod -> mods.add(SelectOption.of(mod.getEffectiveName(), mod.getId())));
        }

        return mods;
    }

}
