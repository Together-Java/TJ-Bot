package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.RoleIcon;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.componentids.Lifespan;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implements the {@code /role-select} command.
 * <p>
 * The command works in two stages. First, a user sets up a role selection dialog by using the
 * command:
 *
 * <pre>
 * {@code
 * /role-select
 *   title: Star Wars
 *   description: Pick your preference
 *   selectable-role: @Jedi
 *   selectable-role1: @Sith
 *   selectable-role2: @Droid
 * }
 * </pre>
 *
 * Afterwards, users can pick their roles in a menu, upon which the command adjusts their roles.
 */
public final class RoleSelectCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RoleSelectCommand.class);

    private static final String TITLE_OPTION = "title";
    private static final String DESCRIPTION_OPTION = "description";
    private static final String ROLE_OPTION = "selectable-role";

    private static final Color AMBIENT_COLOR = new Color(24, 221, 136, 255);

    private static final int OPTIONAL_ROLES_AMOUNT = 22;

    /**
     * Construct an instance.
     */
    public RoleSelectCommand() {
        super("role-select",
                "Creates a dialog that lets users pick roles, system roles are ignored when selected.",
                CommandVisibility.GUILD);

        OptionData roleOption = new OptionData(OptionType.ROLE, ROLE_OPTION,
                "pick roles that users will then be able to select", true);

        getData()
            .addOptions(
                    new OptionData(OptionType.STRING, TITLE_OPTION,
                            "title for the role selection message", true),
                    new OptionData(OptionType.STRING, DESCRIPTION_OPTION,
                            "description for the role selection message", true),
                    roleOption)
            .addOptions(generateMultipleOptions(roleOption, OPTIONAL_ROLES_AMOUNT));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        if (!handleHasPermissions(event)) {
            return;
        }

        List<Role> selectedRoles = getMultipleOptionsByNamePrefix(event, ROLE_OPTION).stream()
            .map(OptionMapping::getAsRole)
            .filter(RoleSelectCommand::handleIsBotAccessibleRole)
            .toList();

        if (!handleAccessibleRolesSelected(event, selectedRoles)) {
            return;
        }

        if (!handleInteractableRolesSelected(event, selectedRoles)) {
            return;
        }

        sendRoleSelectionMenu(event, selectedRoles);
    }

    private boolean handleHasPermissions(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You do not have the required manage role permission to use this command")
                .setEphemeral(true)
                .queue();
            return false;
        }

        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply(
                    "Sorry, but I was not set up correctly. I need the manage role permissions for this.")
                .setEphemeral(true)
                .queue();
            logger.error("The bot requires the manage role permissions for /role-select.");
            return false;
        }

        return true;
    }

    @Contract(pure = true)
    private static boolean handleIsBotAccessibleRole(Role role) {
        boolean isSystemRole = role.isPublicRole() || role.getTags().isBot()
                || role.getTags().isBoost() || role.getTags().isIntegration();

        if (isSystemRole) {
            logger.debug("The {} ({}) role is a system role, and is ignored for /role-select.",
                    role.getName(), role.getId());
        }

        return !isSystemRole;
    }

    private static boolean handleAccessibleRolesSelected(IReplyCallback event,
            Collection<Role> selectedRoles) {
        if (!selectedRoles.isEmpty()) {
            return true;
        }

        MessageEmbed embed = createEmbed("Only system roles selected",
                """
                        I can not interact with system roles, these roles are special roles created by Discord, such as
                        `@everyone`, or the role given automatically to boosters.
                        Please pick non-system roles.""");

        event.replyEmbeds(embed).setEphemeral(true).queue();
        return false;
    }

    private static boolean handleInteractableRolesSelected(IReplyCallback event,
            Collection<Role> selectedRoles) {
        List<Role> nonInteractableRoles = selectedRoles.stream()
            .filter(role -> !event.getGuild().getSelfMember().canInteract(role))
            .toList();

        if (nonInteractableRoles.isEmpty()) {
            return true;
        }

        String nonInteractableRolesText = nonInteractableRoles.stream()
            .map(IMentionable::getAsMention)
            .collect(Collectors.joining(", ", "(", ")"));

        MessageEmbed embed = createEmbed("Lacking permission",
                "I can not interact with %s, please contact someone to give me appropriate permissions or select other roles."
                    .formatted(nonInteractableRolesText));

        event.replyEmbeds(embed).setEphemeral(true).queue();
        return false;
    }

    private void sendRoleSelectionMenu(final CommandInteraction event,
            final Collection<? extends Role> selectableRoles) {
        StringSelectMenu.Builder menu = StringSelectMenu
            .create(generateComponentId(Lifespan.PERMANENT, event.getUser().getId()))
            .setPlaceholder("Select your roles")
            .setMinValues(0)
            .setMaxValues(selectableRoles.size());

        selectableRoles.stream()
            .map(RoleSelectCommand::mapToSelectOption)
            .forEach(menu::addOptions);

        String title =
                event.getOption(TITLE_OPTION, "Select your roles:", OptionMapping::getAsString);
        MessageEmbed embed = createEmbed(title, event.getOption(DESCRIPTION_OPTION).getAsString());

        event.replyEmbeds(embed).addActionRow(menu.build()).queue();
    }

    private static SelectOption mapToSelectOption(Role role) {
        RoleIcon roleIcon = role.getIcon();

        SelectOption option = SelectOption.of(role.getName(), role.getId());
        if (null != roleIcon && roleIcon.isEmoji()) {
            option = option.withEmoji(Emoji.fromUnicode(roleIcon.getEmoji()));
        }

        return option;
    }

    // this should be entity select menu but im just gonna use string rn
    // coz i'll have to do least changes that way
    @Override
    public void onStringSelectSelection(StringSelectInteractionEvent event, List<String> args) {
        Guild guild = event.getGuild();
        List<Role> selectedRoles = event.getSelectedOptions()
            .stream()
            .map(SelectOption::getValue)
            .map(guild::getRoleById)
            .filter(Objects::nonNull)
            .toList();

        if (!handleInteractableRolesSelected(event, selectedRoles)) {
            return;
        }

        handleRoleSelection(event, guild, selectedRoles);
    }

    private static void handleRoleSelection(StringSelectInteractionEvent event, Guild guild,
            Collection<Role> selectedRoles) {
        Collection<Role> rolesToAdd = new ArrayList<>(selectedRoles.size());
        Collection<Role> rolesToRemove = new ArrayList<>(selectedRoles.size());

        // Diff the selected roles from all selectable roles
        event.getInteraction()
            .getComponent()
            .getOptions()
            .stream()
            .map(optionToRole(guild))
            .filter(Optional::isPresent)
            .map(Optional::orElseThrow)
            .forEach(role -> {
                Collection<Role> target = selectedRoles.contains(role) ? rolesToAdd : rolesToRemove;
                target.add(role);
            });

        modifyRoles(event, event.getMember(), guild, rolesToAdd, rolesToRemove);
    }

    private static Function<SelectOption, Optional<Role>> optionToRole(Guild guild) {
        return option -> {
            Role role = guild.getRoleById(option.getValue());

            if (null == role) {
                logger.info(
                        "The {} ({}) role has been removed but is still an option in a selection menu.",
                        option.getLabel(), option.getValue());
            }

            return Optional.ofNullable(role);
        };
    }

    private static void modifyRoles(IReplyCallback event, Member target, Guild guild,
            Collection<Role> rolesToAdd, Collection<Role> rolesToRemove) {
        guild.modifyMemberRoles(target, rolesToAdd, rolesToRemove)
            .flatMap(empty -> event.reply("Your roles have been updated.").setEphemeral(true))
            .queue();
    }

    private static MessageEmbed createEmbed(String title, CharSequence description) {
        return new EmbedBuilder().setTitle(title)
            .setDescription(description)
            .setColor(AMBIENT_COLOR)
            .build();
    }
}
