package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;


/**
 * <p>
 * The implemented command is {@code /kick @user reason}, upon which the bot will kick the user.
 */
public final class KickCommand extends SlashCommandAdapter {
    // the logger
    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";


    /**
     * Creates an instance of the kick command.
     */
    public KickCommand() {
        super("kick", "Use this command to kick a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user which you want to kick", true)
            .addOption(OptionType.STRING, REASON_OPTION, "The reason of the kick", true);
    }

    /**
     * When triggered with {@code /kick @user reason}, the bot will respond then it will check if
     * the user has perms. Then it will check if itself has perms to kick. If it does it will check
     * if the user is too powerful or not. If the user is not then bot will kick the user and reply
     * with {@code Kicked User!}.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        JDA jda = event.getJDA();

        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();

        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();

        String userId = Objects.requireNonNull(user).getUser().getId();

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You do not have the required permissions to kick users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member author = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!author.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("I don't have the required permissions to kick users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!author.canInteract(Objects.requireNonNull(user))) {
            event.reply("This user is too powerful for me to kick.").setEphemeral(true).queue();
            return;
        }

        //tells ths user he has been kicked
        jda.openPrivateChannelById(userId)
                .flatMap(channel -> channel.sendMessage("You have been kicked for this reason " + reason))
                .queue();

        // Kicks the user and send a success response
        event.getGuild()
            .kick(user, reason)
            .flatMap(v -> event.reply("Kicked the user" + user.getUser().getAsTag()))
            .queue();

        // Add this to audit log
        logger.info("User '{}' Kicked user '{}' Reason was '{}'", author, user, reason);
    }
}
