package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


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

    private static final String TITLE_OPTION = "title";
    private static final String DESCRIPTION_OPTION = "description";
    private static final String ROLE_OPTION = "selectable-role";

    private static final Color AMBIENT_COLOR = new Color(24, 221, 136, 255);

    private static final List<OptionData> messageOptions = List.of(
            new OptionData(OptionType.STRING, TITLE_OPTION, "The title for the message", true),
            new OptionData(OptionType.STRING, DESCRIPTION_OPTION, "A description for the message",
                    true));

    /**
     * Amount of times the role-option will be copied ({@value})
     */
    private static final int ROLE_VAR_ARG_OPTION_AMOUNT = 22;


    /**
     * Construct an instance.
     */
    public RoleSelectCommand() {
        super("role-select",
                "Sends a message where users can select their roles, system roles are ignored when selected.",
                SlashCommandVisibility.GUILD);

        OptionData roleOption = new OptionData(OptionType.ROLE, ROLE_OPTION,
                "The role to add to the selection menu", true);

        getData().addOptions(messageOptions)
            .addOptions(roleOption)
            .addOptions(generateMultipleOptions(roleOption, ROLE_VAR_ARG_OPTION_AMOUNT));
    }

    /**
     * Collects the given {@link Collection} of {@link IMentionable IMentionables} to a comma
     * separated String within {@code ()} <br/>
     * It maps the {@link IMentionable IMentionables} to their mention using
     * {@link IMentionable#getAsMention()}.
     *
     * @param mentionables The {@link Collection} of {@link IMentionable IMentionables} to collect
     *        into a {@link String}
     * @return The given mentionables their mention collected into a {@link String}
     */
    private static String mentionablesToJoinedString(
            @NotNull final Collection<? extends IMentionable> mentionables) {
        return mentionables.stream()
            .map(IMentionable::getAsMention)
            .collect(Collectors.joining(", ", "(", ")"));
    }


    /**
     * Handles the event when all the given roles are system roles.
     *
     * @param systemRoles A {@link Collection} of the {@link Role roles} the bot cannot interact
     *        with.
     * @return A modified {@link MessageEmbed} for this error
     */
    private static @NotNull MessageEmbed generateLackingNonSystemRolesEmbed(
            @NotNull final Collection<? extends Role> systemRoles) {

        return makeEmbed("Error: The given roles are all system roles!", """
                The bot can't/shouldn't interact with %s, these roles are created by Discord.
                Examples are @everyone, or the role given automatically to boosters.
                Are you sure you picked the correct role?
                """.formatted(mentionablesToJoinedString(systemRoles)));
    }

    /**
     * Handles the event when the bot cannot interact with certain roles.
     *
     * @param rolesBotCantInteractWith A {@link Collection} of the {@link Role roles} the bot cannot
     *        interact with.
     * @return A modified {@link MessageEmbed} for this error
     */
    private static @NotNull MessageEmbed generateCannotInteractWithRolesEmbed(
            @NotNull final Collection<? extends Role> rolesBotCantInteractWith) {

        return makeEmbed("Error: The role of the bot is too low!",
                "The bot can't interact with %s, contact a staff member to move the bot above these roles."
                    .formatted(mentionablesToJoinedString(rolesBotCantInteractWith)));
    }

    /**
     * Creates an embed to send with the selection menu. <br>
     * This embed is specifically designed for this command and might have unwanted side effects.
     *
     * @param title The title for {@link EmbedBuilder#setTitle(String)}.
     * @param description The description for {@link EmbedBuilder#setDescription(CharSequence)}
     * @return The formatted {@link MessageEmbed}.
     */
    private static @NotNull MessageEmbed makeEmbed(@Nullable final String title,
            @Nullable final CharSequence description) {

        return new EmbedBuilder().setTitle(title)
            .setDescription(description)
            .setColor(AMBIENT_COLOR)
            .setTimestamp(Instant.now())
            .build();
    }


    @Override
    public void onSlashCommand(@NotNull final SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You dont have the required manage role permission to use this command")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("The bot needs the manage role permissions").setEphemeral(true).queue();
            logger.error("The bot needs the manage role permissions");
            return;
        }

        List<Role> rawRoles = getMultipleOptionsByNamePrefix(event, ROLE_OPTION).stream()
            .map(OptionMapping::getAsRole)
            .toList();
        List<Role> roles = filterToBotAccessibleRoles(rawRoles);

        if (roles.isEmpty()) {
            event.replyEmbeds(generateLackingNonSystemRolesEmbed(rawRoles)).queue();
            return;
        }

        List<Role> rolesBotCantInteractWith =
                roles.stream().filter(role -> !selfMember.canInteract(role)).toList();

        if (!rolesBotCantInteractWith.isEmpty()) {
            event.replyEmbeds(generateCannotInteractWithRolesEmbed(rolesBotCantInteractWith))
                .queue();
            return;
        }

        handleCommandSuccess(event, roles);
    }

    /**
     * Filters the given {@link Collection} of {@link Role roles} to not contain system roles. <br>
     * <p>
     * See {@link #handleIsSystemRole(Role)} for more info on what this exactly filters.
     *
     * @param roles The {@link Collection} of the {@link Role roles} to filter
     * @return An unmodifiable {@link List} of all filtered roles
     */
    @NotNull
    private static <T extends Role> List<T> filterToBotAccessibleRoles(
            @NotNull final Collection<T> roles) {

        return roles.stream().filter(RoleSelectCommand::handleIsSystemRole).toList();
    }

    /**
     * Tests the given predicate, if true logs the role as a system role.
     *
     * <p>
     * returns true if one of the following statements is true:
     * <ul>
     * <li>The given role is the {@code @everyone} role</li>
     * <li>The given role is the is a bot/integration role</li>
     * <li>The given role is the booster role</li>
     * <li>The given role is the Twitch Subscriber role</li>
     * </ul>
     *
     * @param role The {@link Role} to test
     * @return Whenever the given {@link Role} is a system-role
     */
    @Contract(pure = true)
    private static boolean handleIsSystemRole(final @NotNull Role role) {
        boolean isSystemRole = role.isPublicRole() || role.getTags().isBot()
                || role.getTags().isBoost() || role.getTags().isIntegration();

        if (isSystemRole) {
            logger.info("The {} ({}) role is a system role, and is ignored", role.getName(),
                    role.getId());
        }

        return !isSystemRole;
    }

    /**
     * Handles the event when no issues were found and the message can be sent.
     *
     * @param event The {@link CommandInteraction} to reply to.
     * @param roles A {@link List} of the {@link Role roles} that the users should be able to pick.
     */
    private void handleCommandSuccess(@NotNull final CommandInteraction event,
            @NotNull final Collection<? extends Role> roles) {

        SelectMenu.Builder menu = SelectMenu.create(generateComponentId(event.getUser().getId()))
            .setPlaceholder("Select your roles")
            .setMaxValues(roles.size())
            .setMinValues(0);

        roles.forEach(role -> menu.addOptions(mapToSelectOption(role)));

        String title = null == event.getOption(TITLE_OPTION) ? "Select your roles:"
                : event.getOption(TITLE_OPTION).getAsString();

        MessageEmbed generatedEmbed =
                makeEmbed(title, event.getOption(DESCRIPTION_OPTION).getAsString());

        event.replyEmbeds(generatedEmbed).addActionRow(menu.build()).queue();
    }

    /**
     * Maps the given role to a {@link SelectOption} with the {@link SelectOption SelectOption's}
     * emoji, if it has one.
     *
     * @param role The {@link Role} to base the option from.
     * @return The generated {@link SelectOption}.
     */
    @NotNull
    private static SelectOption mapToSelectOption(@NotNull final Role role) {
        RoleIcon roleIcon = role.getIcon();

        if (null == roleIcon || !roleIcon.isEmoji()) {
            return SelectOption.of(role.getName(), role.getId());
        } else {
            return SelectOption.of(role.getName(), role.getId())
                .withEmoji((Emoji.fromUnicode(roleIcon.getEmoji())));
        }
    }


    @Override
    public void onSelectionMenu(@NotNull final SelectMenuInteractionEvent event,
            @NotNull final List<String> args) {

        Guild guild = Objects.requireNonNull(event.getGuild(), "The given guild cannot be null");
        List<SelectOption> selectedOptions = Objects.requireNonNull(event.getSelectedOptions(),
                "The given selectedOptions cannot be null");

        List<Role> roles = selectedOptions.stream()
            .map(SelectOption::getValue)
            .map(guild::getRoleById)
            .filter(Objects::nonNull)
            .toList();

        List<Role> rolesBotCantInteractWith =
                roles.stream().filter(role -> !guild.getSelfMember().canInteract(role)).toList();

        if (!rolesBotCantInteractWith.isEmpty()) {
            event.getChannel()
                .sendMessageEmbeds(generateCannotInteractWithRolesEmbed(rolesBotCantInteractWith))
                .queue();
        }

        List<Role> usableRoles =
                roles.stream().filter(role -> guild.getSelfMember().canInteract(role)).toList();

        handleRoleSelection(event, usableRoles, guild);
    }

    /**
     * Handles selection of a {@link SelectMenuInteractionEvent}.
     *
     * @param event the <b>unacknowledged</b> {@link SelectMenuInteractionEvent}.
     * @param selectedRoles The {@link Role roles} selected.
     * @param guild The {@link Guild}.
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
            .forEach((Role role) -> {
                if (selectedRoles.contains(role)) {
                    rolesToAdd.add(role);
                } else {
                    rolesToRemove.add(role);
                }
            });

        handleRoleModifications(event, event.getMember(), guild, rolesToAdd, rolesToRemove);
    }

    /**
     * Creates a function that maps the {@link SelectOption} to a {@link Role} from the given
     * {@link Guild}.
     *
     * @param guild The {@link Guild} to grab the roles from.
     * @return A {@link Function} which maps {@link SelectOption} to the relating {@link Role}.
     */
    @Contract(pure = true)
    @NotNull
    private static Function<SelectOption, Role> roleFromSelectOptionFunction(final Guild guild) {
        return (SelectOption selectedOption) -> {
            Role role = guild.getRoleById(selectedOption.getValue());

            if (null == role) {
                logRemovedRole(selectedOption);
            }

            return role;
        };
    }

    /**
     * Logs that the role of the given {@link SelectOption} doesn't exist anymore.
     *
     * @param selectedOption the {@link SelectOption}
     */
    private static void logRemovedRole(final @NotNull SelectOption selectedOption) {
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
}
