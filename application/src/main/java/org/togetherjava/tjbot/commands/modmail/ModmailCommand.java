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

public class ModmailCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ModmailCommand.class);

    private static final String COMMAND_NAME = "modmail";
    private static final String TARGET_OPTION = "message";
    private static final Config config = Config.getInstance();

    static final List<SelectOption> mods = new ArrayList<>();
    static final Map<String, User> modsMap = new HashMap<>();

    private final JDA jda;

    /**
     * Creates an instance of the ModMail command.
     *
     * @param jda the {@link JDA} instance of all slash commands to find the target guild or the
     *        guild where the moderators are.
     */
    public ModmailCommand(@NotNull JDA jda) {
        super(COMMAND_NAME,
                "sends a message to either a single moderator or on the mod_audit_log channel",
                SlashCommandVisibility.GLOBAL);

        getData().addOption(OptionType.STRING, TARGET_OPTION, "The message to send", true);

        this.jda = Objects.requireNonNull(jda);

        mods.add(SelectOption.of("All Moderators", "all"));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String memberId = event.getUser().getId();

        // checks if selection menu already contains the moderators
        if (mods.size() == 1) {
            loadMenuOptions();
        }

        event.reply("""
                Select the moderator to send message to, or select "All Moderators" to send to
                the guild's mod audit channel.
                """)
            .addActionRow(SelectionMenu
                .create(generateComponentId(memberId, event.getOption(TARGET_OPTION).getAsString()))
                .addOptions(mods)
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
     * This method also stores the moderators on a map for later use. The map's values are always
     * and should be exactly the same with the previous results.
     */
    private void loadMenuOptions() {
        Guild guild = Objects.requireNonNull(jda.getGuildById(config.getGuildId()),
                "A Guild is required to use this command. Perhaps the bot isn't on the guild yet");

        ModMailUtil.loadMenuOptions(guild);
    }

}
