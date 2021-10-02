package org.togetherjava.tjbot.commands.example;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

/**
 * Subcommands example!
 *
 * <p>
 * Discord enforces a strong 100 commands limit globally and a 100 commands limit in each guild. By
 * making usage of subcommands, the amount of commands can be extended significantly allowing many
 * more commands.
 *
 * <p>
 * A good example is the ban command, instead of having tempban, permban separate this can become 1
 * command.
 *
 * <br>
 * <i>counts as 1 command within Discord</i> <br>
 * <br>
 * `{@code ban temp 6d Tijs#0001}` and `{@code ban perm Tijs#0001}`
 *
 * <br>
 * instead of <br>
 * <i>counts as 2 commands within Discord</i> `{@code tempban 6s Tijs#0001}` and
 * `{@code permban Tijs#0001}`
 *
 */
public class SubCommandExample extends SlashCommandAdapter {

    /**
     * Parses the command name and the description to the super's constructor, both as a
     * {@link String}
     */
    public SubCommandExample() {
        super("subcommands-example",
                "Subcommands example! Allows you to ping someone again, and something else?");
    }

    /**
     * Adds the "ping" and "hello" subcommand.
     *
     * @param commandData The thing we will add our sub commands to!
     * @return A {@link CommandData} with the required options for this command
     */
    @Override
    public @NotNull CommandData addOptions(@NotNull CommandData commandData) {
        return commandData.addSubcommands(
                // * adds the subcommand here
                new SubcommandData("ping", "Ping an user! Yes you're allowed to spam again!")
                    // * adds the User option, it's hard to ping nothing, so it's required.
                    .addOption(OptionType.USER, "user", "User to ping", true),
                // * adds another subcommand
                new SubcommandData("hello", "Send hello and optionally you can ping an user.")
                    // * this time it's not required, so no need for that boolean, making it
                    // shorter.
                    .addOption(OptionType.USER, "user", "User to ping!"));
    }

    /**
     * The execute method! <br>
     * Here it checks what subcommand the user ran. <br>
     * After that that command gets ran.
     *
     * @param event A {@link SlashCommandEvent}
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // * get the subcommand
        String subcommandName = event.getSubcommandName();

        if ("ping".equals(subcommandName)) {
            User userToPing = event.getOption("user").getAsUser();

            event.reply(userToPing.getAsMention() + " pinging like this is useless ;)")
                .setEphemeral(true)
                .queue();
        } else if ("hello".equals(subcommandName)) {
            OptionMapping userOption = event.getOption("user");

            if (userOption == null) {
                event.reply("Hello!").queue();
            } else {
                User userToPing = userOption.getAsUser();
                event.reply("Hello " + userToPing.getAsMention() + "!").queue();
            }
        }
    }
}
