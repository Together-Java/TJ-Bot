package org.togetherjava.tjbot.commands.modmail;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
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
import org.togetherjava.tjbot.config.Config;

import java.util.*;

public class ModmailSlashCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ModmailSlashCommand.class);

    private static final String COMMAND_NAME = "modmail";
    private static final String TARGET_OPTION = "message";
    private static final Config config = Config.getInstance();

    private final List<SelectOption> mods = new ArrayList<>();
    private final Map<String, User> modsMap = new HashMap<>();

    private final JDA jda;

    /**
     * Creates the reload command, using the given provider as source of truth for the commands to
     * reload
     *
     * @param jda the jda of slash commands to reload when this command is
     *        triggered
     */
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
            .addActionRow(SelectionMenu
                .create(generateComponentId(memberId, event.getOption(TARGET_OPTION).getAsString()))
                .addOptions(selectionMenuOptions())
                .build())
            .setEphemeral(false)
            .queue();
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        String message = args.get(1);
        // Ignore if another user clicked the button which is only possible when used within the
        // guild.
        String userId = args.get(0);
        if (event.isFromGuild()
                && !userId.equals(Objects.requireNonNull(event.getMember()).getId())) {
            event.reply(
                    "Sorry, but only the user who triggered the command can interact with the menu.")
                .setEphemeral(true)
                .queue();
            return;
        }

        SelectionMenu disabledMenu = event.getSelectionMenu().asDisabled();

        // did user select to send message to all mods
        String modId = event.getValues().get(0);
        if (modId.equals("all")) {
            // currently blocked by #296
            event.reply("Message now sent to all mods").setEphemeral(true).queue();
            return;
        }

        sendToMod(modId, message, event);

        event.getMessage().editMessageComponents(ActionRow.of(disabledMenu)).queue();
        event.reply("Message now sent to moderator").setEphemeral(true).queue();
    }

    private void sendToMod(@NotNull String modId, @NotNull String message,
            @NotNull SelectionMenuEvent event) {
        User mod = modsMap.get(modId);
        if (mod != null) {
            mod.openPrivateChannel().queue(channel -> channel.sendMessage(message).queue());
            return;
        }

        logger
            .warn("""
                    The map storing the moderators is either not in-sync with the list of moderators for the selection menu or
                    an unknown error has occurred.
                    """);

        event.reply("The moderator you chose is not on the list of moderators on the guild")
            .setEphemeral(true)
            .queue();
    }

    /**
     * Creates a list of options containing the moderators for use in the modmail slash command.
     * <p/>
     * If this method has not yet been called prior to calling this method, it will call an
     * expensive query to discord, otherwise, it will return the previous result.
     * <p>
     * This method av lso stores the moderators on a map for later use. The map's values are always
     * and should be exactly the same with the previous results.
     *
     * @return a list of options containing the moderators name to choose from in a selection menu.
     */
    private @NotNull List<SelectOption> selectionMenuOptions() {
        Guild guild = Objects.requireNonNull(jda.getGuildById(config.getGuildId()),
                "Guild ID is required to use this command");

        // checks if method has been called before.
        if (mods.size() == 1) {
            guild.findMembersWithRoles(guild.getRolesByName("moderator", true))
                .get()
                .stream()
                .forEach(mod -> {
                    String modId = mod.getId();
                    mods.add(SelectOption.of(mod.getEffectiveName(), modId));
                    modsMap.put(modId, mod.getUser());
                });
        }

        return mods;
    }

}
