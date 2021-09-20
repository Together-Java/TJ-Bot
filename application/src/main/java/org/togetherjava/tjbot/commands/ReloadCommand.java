package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ReloadCommand(CommandHandler commandHandler) implements Command {

    @Override
    public @NotNull String getCommandName() {
        return "reload";
    }

    @Override
    public @NotNull String getDescription() {
        return "Uploads all existing slash-commands to Discord so they're fully up-to-date.";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        Member member = event.getMember();

        if (member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(
                    "You sure? You can only reload commands a few times each day, so don't overdo this.")
                .addActionRow(
                        Button.of(ButtonStyle.SUCCESS, generateComponentId(member.getId()),
                                "Affirmative!"),
                        Button.of(ButtonStyle.DANGER, generateComponentId(member.getId()),
                                "Wrong command! Sorry"))
                .queue();
        } else {
            event.reply("You need the MANAGE_SERVER permission to use this command!")
                .setEphemeral(true)
                .queue();
        }
    }

    @SuppressWarnings("squid:S1301") // it is easier to read here
    @Override
    public void onButtonClick(ButtonClickEvent event, List<String> idArgs) {
        Member member = event.getMember();

        if (member.getId().equals(idArgs.get(0))) {
            switch (event.getButton().getStyle()) {
                case DANGER -> {
                    event.reply("Next time use the right command!").queue();
                    event.getMessage().editMessageComponents().queue();
                }
                case SUCCESS -> {
                    event.deferReply().queue();
                    List<Command> commands = commandHandler.getCommandList();
                    List<CommandListUpdateAction> restActions;

                    // * loads all RestActions from updating the Guild and Global commands
                    restActions = getGuildCommandUpdateRestActions(event, commands);
                    restActions.add(getGlobalCommandUpdateRestAction(event, commands));

                    // * Triggers all RestActions, when they're all finished the message gets send.
                    RestAction.allOf(restActions)
                        .queue(updatedCommands -> event.getHook()
                            .editOriginal(
                                    "Commands successfully reloaded! *Global commands can take upto 1 hour to load*")
                            .queue());
                }
                default -> event.reply("I am not sure what you clicked?")
                    .setEphemeral(true)
                    .queue();
            }
        }
    }

    /**
     * Adds commands to {@link net.dv8tion.jda.api.JDA}
     * 
     * @param event A {@link Event}
     * @param commands A {@link List} of {@link Command Commands}
     * @return newly created {@link CommandListUpdateAction} with Commands
     */
    private CommandListUpdateAction getGlobalCommandUpdateRestAction(Event event,
            List<Command> commands) {
        return addCommandsToUpdateAction(event.getJDA().updateCommands(), commands, false);
    }

    /**
     * Adds commands to all {@link Guild Guilds}
     * 
     * @param event A {@link Event}
     * @param commands A {@link List} of {@link Command Commands}
     * @return newly created {@link CommandListUpdateAction} with Commands
     */
    private List<CommandListUpdateAction> getGuildCommandUpdateRestActions(Event event,
            List<Command> commands) {
        SnowflakeCacheView<Guild> guildCache = event.getJDA().getGuildCache();

        List<CommandListUpdateAction> restActions = new ArrayList<>((int) guildCache.size());

        event.getJDA()
            .getGuildCache()
            .forEach(guild -> restActions
                .add(addCommandsToUpdateAction(guild.updateCommands(), commands, true)));

        return restActions;
    }

    /**
     * Adds all given commands to the {@link CommandListUpdateAction} if <br>
     * The {@link Command} <b>AND</b> the {@link CommandListUpdateAction} are from a {@link Guild}
     * <br>
     * or <br>
     * The {@link Command} <b>AND</b> the {@link CommandListUpdateAction} are <b>NOT</b> from a
     * {@link Guild} <br>
     *
     * @param commandListUpdateAction A {@link CommandListUpdateAction}
     * @param commands A {@link List} of {@link Command Commands} that need to be added
     * @param isGuild Whenever the given {@link CommandListUpdateAction} is from a {@link Guild} or
     *        not
     * @return The updated {@link CommandListUpdateAction}
     */
    private CommandListUpdateAction addCommandsToUpdateAction(
            CommandListUpdateAction commandListUpdateAction, List<Command> commands,
            boolean isGuild) {
        commands.forEach(command -> {
            if (command.isGuildOnly() == isGuild) {
                commandListUpdateAction.addCommands(command.addOptions(
                        new CommandData(command.getCommandName(), command.getDescription())));
            }
        });


        return commandListUpdateAction;
    }
}
