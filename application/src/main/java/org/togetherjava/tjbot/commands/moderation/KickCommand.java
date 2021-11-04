package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import java.util.Objects;


/**
 * This command can kicks users. Kicking can also be paired with a kick reason. The command will
 * also try to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either kick other users or
 * to kick the specific given user (for example a moderator attempting to kick an admin).
 *
 */
public final class KickCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";

    /**
     * Constructs an instance.
     */
    public KickCommand() {
        super("kick", "Kicks the given user from the server", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to kick", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be kicked", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(USER_OPTION), "The target is null");
        Member target = userOption.getAsMember();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");

        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();


        Guild guild = event.getGuild();
        Member bot = guild.getSelfMember();

        if (!author.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "You can not kick users in this guild since you do not have the KICK_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return;
        }

        String userTag = userOption.getAsUser().getAsTag();
        if (!author.canInteract(target)) {
            event.reply("The user " + userTag + " is too powerful for you to kick.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!bot.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "I can not kick users in this guild since I do not have the KICK_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have KICK_MEMBERS permission on the server '{}' ",
                    event.getGuild().getName());
            return;
        }

        if (!bot.canInteract(target)) {
            event.reply("The user " + userTag + " is too powerful for me to kick.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!ModerationUtils.handleReason(reason, event)) {
            return;
        }

        kickUser(target, author, reason, guild, event);
    }

    private static void kickUser(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        event.getJDA()
            .openPrivateChannelById(target.getUser().getId())
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been kicked from the server %s.
                            If you think this was a mistake, please contact a moderator or admin of the server.
                            The reason for the kick is: %s
                            """
                        .formatted(guild.getName(), reason)))
            .mapToResult()
            .flatMap(result -> guild.kick(target, reason).reason(reason))
            .flatMap(v -> event.reply(target.getUser().getAsTag() + " was kicked by "
                    + author.getUser().getAsTag() + " for: " + reason))
            .queue();

        logger.info(" '{} ({})' kicked the user '{} ({})' due to reason being '{}'",
                author.getUser().getAsTag(), author.getIdLong(), target.getUser().getAsTag(),
                target.getUser().getId(), reason);
    }
}
