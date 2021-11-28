package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Implements the {@code roleselect} command.
 *
 * <p>
 * Allows users to select their roles without using reactions, instead it uses selection menus where
 * you can select multiple roles. <br />
 * Note: the bot can only use roles below its highest one
 */
public class RoleSelectCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RoleSelectCommand.class);

    private static final String ALL_OPTION = "all";
    private static final String CHOOSE_OPTION = "choose";

    private static final String TITLE_OPTION = "title";
    private static final String DESCRIPTION_OPTION = "description";

    private static final List<OptionData> messageOptions = List.of(
            new OptionData(OptionType.STRING, TITLE_OPTION, "The title for the message", false),
            new OptionData(OptionType.STRING, DESCRIPTION_OPTION, "A description for the message",
                    false));


    /**
     * Construct an instance
     *
     * @see RoleSelectCommand
     */
    public RoleSelectCommand() {
        super("role-select", "Sends a message where users can select their roles",
                SlashCommandVisibility.GUILD);

        SubcommandData allRoles =
                new SubcommandData(ALL_OPTION, "Lists all the rolls in the server for users")
                    .addOptions(messageOptions);

        SubcommandData selectRoles =
                new SubcommandData(CHOOSE_OPTION, "Choose the roles for users to select")
                    .addOptions(messageOptions);

        getData().addSubcommands(allRoles, selectRoles);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member member = Objects.requireNonNull(event.getMember(), "Member is null");
        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You dont have the right permissions to use this command")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member selfMember = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("The bot needs the manage role permissions").setEphemeral(true).queue();
            logger.warn("The bot needs the manage role permissions");
            return;
        }

        SelectionMenu.Builder menu = SelectionMenu.create(generateComponentId(member.getId()));
        boolean ephemeral = false;

        if (Objects.equals(event.getSubcommandName(), CHOOSE_OPTION)) {
            addMenuOptions(event, menu, "Select the roles to display", 1);
            ephemeral = true;
        } else {
            addMenuOptions(event, menu, "Select your roles", 0);
        }

        // Handle Optional arguments
        OptionMapping titleOption = event.getOption(TITLE_OPTION);
        OptionMapping descriptionOption = event.getOption(DESCRIPTION_OPTION);

        String title = handleOption(titleOption);
        String description = handleOption(descriptionOption);

        event.replyEmbeds(makeEmbed(title, description))
            .addActionRow(menu.build())
            .setEphemeral(ephemeral)
            .queue();
    }

    /**
     * Adds role options to a selection menu
     * <p>
     *
     * @param event the {@link SlashCommandEvent}
     * @param menu the menu to add options to {@link SelectionMenu.Builder}
     * @param placeHolder the placeholder for the menu {@link String}
     * @param minValues the minimum number of selections. nullable {@link Integer}
     */
    private static void addMenuOptions(@NotNull SlashCommandEvent event,
            @NotNull SelectionMenu.Builder menu, @NotNull String placeHolder,
            @Nullable Integer minValues) {

        Role highestBotRole =
                Objects.requireNonNull(event.getGuild()).getSelfMember().getRoles().get(1);
        List<Role> guildRoles = Objects.requireNonNull(event.getGuild()).getRoles();

        List<Role> roles = new ArrayList<>(
                guildRoles.subList(guildRoles.indexOf(highestBotRole) + 1, guildRoles.size()));

        if (minValues != null) {
            menu.setMinValues(minValues);
        }

        menu.setPlaceholder(placeHolder).setMaxValues(roles.size());

        for (Role role : roles) {
            if (role.getName().equals("@everyone"))
                continue;
            menu.addOption(role.getName(), role.getId());
        }
    }

    /**
     * Creates an embedded message to send with the selection menu
     *
     * <p>
     * </p>
     *
     * @param title for the embedded message. nullable {@link String}
     * @param description for the embedded message. nullable {@link String}
     * @return the formatted embed {@link MessageEmbed}
     */
    private static @NotNull MessageEmbed makeEmbed(@Nullable String title,
            @Nullable String description) {

        if (title == null) {
            title = "Select your roles";
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(title);

        if (description != null) {
            embedBuilder.setDescription(description);
        }

        embedBuilder.setColor(new Color(24, 221, 136));

        return embedBuilder.build();
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        Member member = Objects.requireNonNull(event.getMember(), "Member is null");

        // Get the roles the bot can interact with
        List<Role> guildRoles = Objects.requireNonNull(event.getGuild()).getRoles();
        List<String> roleIds = guildRoles.stream().map(Role::getId).collect(Collectors.toList());

        List<Role> roles = new ArrayList<>();
        for (SelectOption role : Objects.requireNonNull(event.getSelectedOptions())) {
            if (roleIds.contains(role.getValue())) {
                roles.add(guildRoles.get(roleIds.indexOf(role.getValue())));
            }
        }

        // True if the event option was 'choose'
        if (event.getMessage().isEphemeral()) {

            SelectionMenu.Builder menu = SelectionMenu.create(generateComponentId(member.getId()));
            menu.setPlaceholder("Select your roles").setMaxValues(roles.size());

            for (SelectOption roleOption : Objects.requireNonNull(event.getSelectedOptions())) {
                Role role = guildRoles.get(roleIds.indexOf(roleOption.getValue()));
                menu.addOption(role.getName(), role.getId());
            }

            event.replyEmbeds(event.getMessage().getEmbeds().get(0))
                .addActionRow(menu.build())
                .queue();

            return;
        }

        // Add the selected roles to the member
        for (Role role : guildRoles) {
            if (roles.contains(role)) {
                Objects.requireNonNull(event.getGuild()).addRoleToMember(member, role).queue();
            }
        }

        event.reply("Added your roles!").setEphemeral(true).queue();
    }

    /**
     * This gets the OptionMapping and returns the value as a string if there is one
     *
     * <p>
     * </p>
     *
     * @param option the {@link OptionMapping}
     * @return the value. nullable {@link String}
     */
    private static @Nullable String handleOption(@Nullable OptionMapping option) {

        if (option == null) {
            return null;
        }

        if (option.getType() == OptionType.STRING) {
            return option.getAsString();
        } else if (option.getType() == OptionType.BOOLEAN) {
            return option.getAsBoolean() ? "true" : "false";
        } else {
            return null;
        }
    }
}
