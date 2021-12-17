package org.togetherjava.tjbot.commands.modmail;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Command that either sends a direct message to a moderator or sends the message to the dedicated
 * mod audit channel by the moderators.
 */
public class ModmailCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ModmailCommand.class);

    private static final String COMMAND_NAME = "modmail";
    private static final String TARGET_OPTION = "message";
    private static final Config config = Config.getInstance();

    final List<SelectOption> mods = new ArrayList<>();

    /**
     * Creates an instance of the ModMail command.
     */
    public ModmailCommand() {
        super(COMMAND_NAME,
                "sends a message to either a single moderator or on the mod_audit_log channel",
                SlashCommandVisibility.GLOBAL);

        getData().addOption(OptionType.STRING, TARGET_OPTION, "The message to send", true);

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
                .addOptions(mods)
                .build())
            .setEphemeral(false)
            .queue();
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        String message = args.get(1);
        // Ignore if another user clicked the button.
        String userId = args.get(0);
        if (!userId.equals(event.getUser().getId())) {
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

        // disable selection menu
        event.getMessage().editMessageComponents(ActionRow.of(disabledMenu)).queue();

        boolean wasSent = sendToMod(modId, message, event);
        if (!wasSent) {
            event.reply("The moderator you chose was not found on the guild.")
                .setEphemeral(true)
                .queue();

            String modSelectedByUser = event.getSelectedOptions().get(0).getLabel();
            logger.warn("""
                    Moderator '{}' chosen by user is not on the guild. Use the /reloadmod command
                    to update the list of moderators.
                    """, modSelectedByUser);

            return;
        }

        event.reply("Message now sent to selected moderator").setEphemeral(true).queue();
    }

    /**
     * Populates the list of moderators and stores it into a list to avoid querying an expensive
     * call to discord everytime the command is used.
     *
     * @param event the event that triggered this method
     */
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        Guild guild = event.getJDA().getGuildById(config.getGuildId());
        ModmailUtil.listOfMod(guild, mods);
    }

    private boolean sendToMod(@NotNull String modId, @NotNull String message,
            @NotNull SelectionMenuEvent event) {
        // the else is when the user invoked the command not on the context of a guild.
        Guild guild = Objects.requireNonNullElse(event.getGuild(),
                event.getJDA().getGuildById(config.getGuildId()));

        return !guild.retrieveMemberById(modId)
            .submit()
            .thenCompose(user -> user.getUser().openPrivateChannel().submit())
            .thenAccept(channel -> channel
                .sendMessageEmbeds(ModmailUtil.messageEmbed(event.getUser().getName(), message))
                .queue())
            .whenComplete((v, err) -> {
                if (err != null)
                    err.printStackTrace();
            })
            .isCompletedExceptionally();
    }

    /**
     * Reloads the list of moderators to choose from from the {@link ModmailCommand}.
     * <p>
     * Only members who have the Moderator role can use this command.
     */
    public class ReloadModmailCommand extends SlashCommandAdapter {

        private static final String COMMAND_NAME = "reloadmod";

        public ReloadModmailCommand() {
            super(COMMAND_NAME, "reloads the moderators in the modmail command",
                    SlashCommandVisibility.GUILD);
        }

        @Override
        public void onSlashCommand(@NotNull SlashCommandEvent event) {
            mods.clear();
            ModmailUtil.listOfMod(event.getGuild(), mods);

            if (ModmailUtil.doesUserHaveModRole(event.getMember(), event.getGuild())) {
                event.reply("List of moderators has now been updated.").setEphemeral(true).queue();
                return;
            }

            event.reply("Only moderators can use this command.").setEphemeral(true).queue();
        }

    }


}
