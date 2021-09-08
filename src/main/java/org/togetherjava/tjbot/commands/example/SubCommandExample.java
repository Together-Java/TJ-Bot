package org.togetherjava.tjbot.commands.example;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.togetherjava.tjbot.commands.ICommand;

/**
 * Subcommands example!
 *
 * <p>
 *     Discord enforces a strong 100 commands limit globally and a 100 commands limit in each guild.
 *     By making usage of subcommands, the amount of commands can be extended significantly allowing many many more commands.
 *
 * <p>
 *     A good example is the ban command, instead of having tempban, permban separate this can become 1 command.
 *
 * <br> <i>counts as 1 command within Discord</i> <br>
 * <br> `{@code ban temp 6d Tijs#0001}` and `{@code ban perm Tijs#0001}`
 *
 * <br> instead of
 * <br> <i>counts as 2 commands within Discord</i>
 * `{@code tempban 6s Tijs#0001}` and `{@code permban Tijs#0001}`
 *
 */
public class SubCommandExample implements ICommand {

    /**
     * The command name is subcommands-example, so it returns subcommands-example
     *
     * @return
     *  {@link String} with the command's name
     */
    @Override
    public String getCommandName() {
        return "subcommands-example";
    }

    /**
     * The description!
     *
     * @return
     *  A {@link String} with the description
     */
    @Override
    public String getDescription() {
        return "Subcommands example! Allows you to ping someone again, and something else?";
    }

    /**
     * Adds the "ping" and "hello" subcommand.
     *
     * @param commandData
     *  The thing we will add our sub commands to!
     * @return
     *  A {@link CommandData} with the required options for this command
     */
    @Override
    public CommandData addOptions(CommandData commandData) {
        return commandData.addSubcommands(
                // * adds the subcommand here
                new SubcommandData("ping", "Ping an user! Yes you're allowed to spam again!")
                        // * adds the User option, it's hard to ping nothing so it's required.
                        .addOption(OptionType.USER, "User", "User to ping", true),
                // * adds another subcommand
                new SubcommandData("hello", "Send hello and optionally you can ping an user.")
                        // * this time it's not required, so no need for that boolean, making it shorter.
                        .addOption(OptionType.USER, "User", "User to ping!")
        );
    }

    /**
     * The execute method!
     * <br> Here it check what subcommand the user ran.
     * <br> After that that command gets ran.
     *
     * @param event
     *  A {@link SlashCommandEvent}
     */
    @Override
    public void execute(SlashCommandEvent event) {
        // * get the subcommand
        switch(event.getSubcommandName()) {
            // * if it's "ping", run the ping command.
            case "ping" -> {
                User userToPing = event.getOption("User").getAsUser();

                event.reply(userToPing.getAsMention() + " pinging like this is useless ;)").setEphemeral(true).queue();
            }

            // * if it's "Hello", run the hello command.
            case "hello" -> {
                OptionMapping userOption = event.getOption("User");

                if (userOption == null) {
                    event.reply("Hello!").queue();
                } else {
                    User userToPing = userOption.getAsUser();
                    event.reply("Hello " + userToPing.getAsMention() + "!").queue();
                }
            }
        }
    }
}
