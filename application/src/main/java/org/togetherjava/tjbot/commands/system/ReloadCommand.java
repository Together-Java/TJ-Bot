package org.togetherjava.tjbot.commands.system;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.utils.MessageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The reload command is a meta command that updates all commands to Discord by using
 * {@link CommandListUpdateAction} to push any changes through JDA.
 * <p>
 * Whenever a command has been added, removed or changed, the reload command has to be called for
 * the changes to actually take effect in Discord.
 * <p>
 * This command is rate limited by Discord and may not be used too often. Be aware that it will
 * reload all commands registered with this application, on all guilds.
 */
public final class ReloadCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReloadCommand.class);
    private final SlashCommandProvider commandProvider;

    /**
     * Creates the reload command, using the given provider as source of truth for the commands to
     * reload
     *
     * @param commandProvider the provider of slash commands to reload when this command is
     *        triggered
     */
    public ReloadCommand(@NotNull SlashCommandProvider commandProvider) {
        super("reload",
                "Uploads all existing slash-commands to Discord so they are fully up-to-date.",
                SlashCommandVisibility.GUILD);
        this.commandProvider = commandProvider;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member member = Objects.requireNonNull(event.getMember());

        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            logger.debug(
                    "Attempted reload but is missing permissions, triggered by user '{}' in guild '{}'",
                    member.getId(), event.getGuild());
            event.reply("You need the 'MANAGE_SERVER' permission to use this command.")
                .setEphemeral(true)
                .queue();
            return;
        }

        event.reply(
                "Are you sure? You can only reload commands a few times each day, so do not overdo this.")
            .addActionRow(
                    Button.of(ButtonStyle.SUCCESS, generateComponentId(member.getId()), "Yes"),
                    Button.of(ButtonStyle.DANGER, generateComponentId(member.getId()), "No"))
            .queue();
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
        // Ignore if another user clicked the button
        String userId = args.get(0);
        if (!userId.equals(Objects.requireNonNull(event.getMember()).getId())) {
            event.reply("Sorry, but only the user who triggered the command can use these buttons.")
                .setEphemeral(true)
                .queue();
            return;
        }

        ButtonStyle buttonStyle = Objects.requireNonNull(event.getButton()).getStyle();
        switch (buttonStyle) {
            case DANGER -> {
                event.reply("Okay, will not reload.").queue();
            }
            case SUCCESS -> {
                logger.info("Reloading commands, triggered by user '{}' in guild '{}'", userId,
                        event.getGuild());
                event.deferReply().queue();
                List<CommandListUpdateAction> actions =
                        Collections.synchronizedList(new ArrayList<>());

                // Reload global commands
                actions.add(updateCommandsIf(
                        command -> command.getVisibility() == SlashCommandVisibility.GLOBAL,
                        getGlobalUpdateAction(event.getJDA())));

                // Reload guild commands (potentially many guilds)
                // NOTE Storing the guild actions in a list is potentially dangerous since the
                // bot
                // might theoretically be part of so many guilds that it exceeds the max size of
                // list. However, correctly reducing RestActions in a stream is not trivial.
                getGuildUpdateActions(event.getJDA())
                    .map(updateAction -> updateCommandsIf(
                            command -> command.getVisibility() == SlashCommandVisibility.GUILD,
                            updateAction))
                    .forEach(actions::add);
                logger.debug("Reloading commands over {} action-upstreams", actions.size());

                // Send message when all are done
                RestAction.allOf(actions)
                    .queue(updatedCommands -> event.getHook()
                        .editOriginal(
                                "Commands successfully reloaded! (global commands can take up to one hour to load)")
                        .queue());
            }
            default -> throw new AssertionError("Unexpected button action clicked: " + buttonStyle);
        }

        MessageUtils.disableButtons(event.getMessage());
    }

    /**
     * Updates all commands given by the command provider which pass the given filter by pushing
     * through the given action upstream.
     *
     * @param commandFilter filter that matches commands that should be uploaded
     * @param updateAction the upstream to update commands
     * @return the given upstream for chaining
     */
    private @NotNull CommandListUpdateAction updateCommandsIf(
            @NotNull Predicate<? super SlashCommand> commandFilter,
            @NotNull CommandListUpdateAction updateAction) {
        return commandProvider.getSlashCommands()
            .stream()
            .filter(commandFilter)
            .map(SlashCommand::getData)
            .reduce(updateAction, CommandListUpdateAction::addCommands, (x, y) -> x);
    }

    private static @NotNull CommandListUpdateAction getGlobalUpdateAction(@NotNull JDA jda) {
        return jda.updateCommands();
    }

    private static @NotNull Stream<CommandListUpdateAction> getGuildUpdateActions(
            @NotNull JDA jda) {
        return jda.getGuildCache().stream().map(Guild::updateCommands);
    }
}
