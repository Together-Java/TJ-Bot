package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenuInteraction;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.componentids.Lifespan;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;


/**
 * Implements the {@code roleSelect} command.
 *
 * <p>
 * Allows users to select their roles without using reactions, instead it uses selection menus where
 * you can select multiple roles. <br />
 * Note: the bot can only use roles with a position below its highest one
 */
public final class RoleSelectCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RoleSelectCommand.class);

    private static final String ALL_OPTION = "all";
    private static final String CHOOSE_OPTION = "choose";

    private static final String TITLE_OPTION = "title";
    private static final String DESCRIPTION_OPTION = "description";

    private static final Color AMBIENT_COLOR = new Color(24, 221, 136, 255);

    private static final List<OptionData> messageOptions = List.of(
            new OptionData(OptionType.STRING, TITLE_OPTION, "The title for the message", false),
            new OptionData(OptionType.STRING, DESCRIPTION_OPTION, "A description for the message",
                    false));


    /**
     * Construct an instance.
     */
    public RoleSelectCommand() {
        super("role-select", "Sends a message where users can select their roles",
                CommandVisibility.GUILD);

        SubcommandData allRoles =
                new SubcommandData(ALL_OPTION, "Lists all the rolls in the server for users")
                    .addOptions(messageOptions);

        SubcommandData selectRoles =
                new SubcommandData(CHOOSE_OPTION, "Choose the roles for users to select")
                    .addOptions(messageOptions);

        getData().addSubcommands(allRoles, selectRoles);
    }

    @NotNull
    private static SelectOption mapToSelectOption(@NotNull Role role) {
        RoleIcon roleIcon = role.getIcon();

        if (null == roleIcon || !roleIcon.isEmoji()) {
            return SelectOption.of(role.getName(), role.getId());
        } else {
            return SelectOption.of(role.getName(), role.getId())
                .withEmoji((Emoji.fromUnicode(roleIcon.getEmoji())));
        }
    }

    @Override
    public void onSlashCommand(@NotNull final SlashCommandInteractionEvent event) {
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
            logger.error("The bot needs the manage role permissions");
            return;
        }

        SelectMenu.Builder menu =
                SelectMenu.create(generateComponentId(Lifespan.PERMANENT, member.getId()));
        boolean isEphemeral = false;

        if (CHOOSE_OPTION.equals(event.getSubcommandName())) {
            addMenuOptions(event, menu, "Select the roles to display", 1);
            isEphemeral = true;
        } else {
            addMenuOptions(event, menu, "Select your roles", 0);
        }

        // Handle Optional arguments
        OptionMapping titleOption = event.getOption(TITLE_OPTION);
        OptionMapping descriptionOption = event.getOption(DESCRIPTION_OPTION);

        String title = handleOption(titleOption);
        String description = handleOption(descriptionOption);

        MessageBuilder messageBuilder = new MessageBuilder(makeEmbed(title, description))
            .setActionRows(ActionRow.of(menu.build()));

        if (isEphemeral) {
            event.reply(messageBuilder.build()).setEphemeral(true).queue();
        } else {
            event.getChannel().sendMessage(messageBuilder.build()).queue();

            event.reply("Message sent successfully!").setEphemeral(true).queue();
        }
    }

    /**
     * Adds role options to a selection menu.
     * <p>
     *
     * @param event the {@link SlashCommandInteractionEvent}
     * @param menu the menu to add options to {@link SelectMenu.Builder}
     * @param placeHolder the placeholder for the menu {@link String}
     * @param minValues the minimum number of selections. nullable {@link Integer}
     */
    private static void addMenuOptions(@NotNull final Interaction event,
            @NotNull final SelectMenu.Builder menu, @NotNull final String placeHolder,
            @Nullable final Integer minValues) {

        Guild guild = Objects.requireNonNull(event.getGuild(), "The given guild cannot be null");

        Role highestBotRole = guild.getSelfMember().getRoles().get(0);
        List<Role> guildRoles = guild.getRoles();

        Collection<Role> roles = new ArrayList<>(
                guildRoles.subList(guildRoles.indexOf(highestBotRole) + 1, guildRoles.size()));

        if (null != minValues) {
            menu.setMinValues(minValues);
        }

        menu.setPlaceholder(placeHolder)
            .setMaxValues(roles.size())
            .addOptions(roles.stream()
                .filter(role -> !role.isPublicRole())
                .filter(role -> !role.getTags().isBot())
                .map(RoleSelectCommand::mapToSelectOption)
                .toList());
    }

    /**
     * Creates an embedded message to send with the selection menu.
     *
     * @param title for the embedded message. nullable {@link String}
     * @param description for the embedded message. nullable {@link String}
     * @return the formatted embed {@link MessageEmbed}
     */
    private static @NotNull MessageEmbed makeEmbed(@Nullable final String title,
            @Nullable final CharSequence description) {

        String effectiveTitle = (null == title) ? "Select your roles:" : title;

        return new EmbedBuilder().setTitle(effectiveTitle)
            .setDescription(description)
            .setColor(AMBIENT_COLOR)
            .build();
    }

    @Override
    public void onSelectionMenu(@NotNull final SelectMenuInteractionEvent event,
            @NotNull final List<String> args) {

        Guild guild = Objects.requireNonNull(event.getGuild(), "The given guild cannot be null");
        List<SelectOption> selectedOptions = Objects.requireNonNull(event.getSelectedOptions(),
                "The given selectedOptions cannot be null");

        List<Role> selectedRoles = selectedOptions.stream()
            .map(SelectOption::getValue)
            .map(guild::getRoleById)
            .filter(Objects::nonNull)
            .filter(role -> guild.getSelfMember().canInteract(role))
            .toList();


        if (event.getMessage().isEphemeral()) {
            handleNewRoleBuilderSelection(event, selectedRoles);
        } else {
            handleRoleSelection(event, selectedRoles, guild);
        }
    }

    /**
     * Handles selection of a {@link SelectMenuInteractionEvent}.
     *
     * @param event the <b>unacknowledged</b> {@link SelectMenuInteractionEvent}
     * @param selectedRoles the {@link Role roles} selected
     * @param guild the {@link Guild}
     */
    private static void handleRoleSelection(final @NotNull SelectMenuInteractionEvent event,
            final @NotNull Collection<Role> selectedRoles, final Guild guild) {
        Collection<Role> rolesToAdd = new ArrayList<>(selectedRoles.size());
        Collection<Role> rolesToRemove = new ArrayList<>(selectedRoles.size());

        event.getInteraction()
            .getComponent()
            .getOptions()
            .stream()
            .map(roleFromSelectOptionFunction(guild))
            .filter(Objects::nonNull)
            .forEach(role -> {
                if (selectedRoles.contains(role)) {
                    rolesToAdd.add(role);
                } else {
                    rolesToRemove.add(role);
                }
            });

        handleRoleModifications(event, event.getMember(), guild, rolesToAdd, rolesToRemove);
    }

    @NotNull
    private static Function<SelectOption, Role> roleFromSelectOptionFunction(Guild guild) {
        return selectedOption -> {
            Role role = guild.getRoleById(selectedOption.getValue());

            if (null == role) {
                handleNullRole(selectedOption);
            }

            return role;
        };
    }

    /**
     * Handles the selection of the {@link SelectMenu} if it came from a builder.
     *
     * @param event the <b>unacknowledged</b> {@link ComponentInteraction}
     * @param selectedRoles the {@link Role roles} selected by the {@link User} from the
     *        {@link ComponentInteraction} event
     */
    private void handleNewRoleBuilderSelection(@NotNull final ComponentInteraction event,
            final @NotNull Collection<? extends Role> selectedRoles) {
        SelectMenu.Builder menu = SelectMenu.create(generateComponentId(event.getUser().getId()))
            .setPlaceholder("Select your roles")
            .setMaxValues(selectedRoles.size())
            .setMinValues(0);

        selectedRoles.forEach(role -> menu.addOption(role.getName(), role.getId()));

        event.getChannel()
            .sendMessageEmbeds(event.getMessage().getEmbeds().get(0))
            .setActionRow(menu.build())
            .queue();

        event.reply("Message sent successfully!").setEphemeral(true).queue();
    }

    /**
     * Logs that the role of the given {@link SelectOption} doesn't exist anymore.
     *
     * @param selectedOption the {@link SelectOption}
     */
    private static void handleNullRole(final @NotNull SelectOption selectedOption) {
        logger.info(
                "The {} ({}) role has been removed but is still an option in the selection menu",
                selectedOption.getLabel(), selectedOption.getValue());
    }

    /**
     * Updates the roles of the given member.
     *
     * @param event an <b>unacknowledged</b> {@link Interaction} event
     * @param member the member to update the roles of
     * @param guild what guild to update the roles in
     * @param additionRoles the roles to add
     * @param removalRoles the roles to remove
     */
    private static void handleRoleModifications(@NotNull final IReplyCallback event,
            final Member member, final @NotNull Guild guild, final Collection<Role> additionRoles,
            final Collection<Role> removalRoles) {
        guild.modifyMemberRoles(member, additionRoles, removalRoles)
            .flatMap(empty -> event.reply("Your roles have been updated!").setEphemeral(true))
            .queue();
    }

    /**
     * This gets the OptionMapping and returns the value as a string if there is one.
     *
     * @param option the {@link OptionMapping}
     * @return the value. nullable {@link String}
     */
    @Contract("null -> null")
    private static @Nullable String handleOption(@Nullable final OptionMapping option) {
        if (null == option) {
            return null;
        }

        if (OptionType.STRING == option.getType()) {
            return option.getAsString();
        } else if (OptionType.BOOLEAN == option.getType()) {
            return option.getAsBoolean() ? "true" : "false";
        } else {
            return null;
        }
    }
}
