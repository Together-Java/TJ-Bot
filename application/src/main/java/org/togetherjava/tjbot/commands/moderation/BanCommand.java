package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;

/**
 * This command can ban users and optionally remove their messages from the past days. Banning can
 * also be paired with a ban reason. The command will also try to DM the user to inform them about
 * the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either ban other users or
 * to ban the specific given user (for example a moderator attempting to ban an admin).
 */
public final class BanCommand extends SlashCommandAdapter {
    private static final String TARGET_OPTION = "user";
    private static final String DELETE_HISTORY_OPTION = "delete-history";
    private static final String REASON_OPTION = "reason";
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);

    /**
     * Constructs an instance.
     */
    public BanCommand() {
        super("ban", "Bans the given user from the server", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to ban", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be banned", true)
            .addOptions(new OptionData(OptionType.INTEGER, DELETE_HISTORY_OPTION,
                    "the amount of days of the message history to delete, none means no messages are deleted.",
                    true).addChoice("none", 0).addChoice("recent", 1).addChoice("all", 7));
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private static boolean handleCanInteractWithTarget(@NotNull Member bot, @NotNull Member author,
            @NotNull Member target, @NotNull Interaction event) {
        String targetTag = target.getUser().getAsTag();
        if (!author.canInteract(target)) {
            event.reply("The user " + targetTag + " is too powerful for you to ban.")
                .setEphemeral(true)
                .queue();
            return false;
        }

        if (!bot.canInteract(target)) {
            event.reply("The user " + targetTag + " is too powerful for me to ban.")
                .setEphemeral(true)
                .queue();
            return false;
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private static boolean handleHasPermissions(@NotNull IPermissionHolder bot,
            @NotNull IPermissionHolder author, @NotNull Guild guild, @NotNull Interaction event) {
        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "You can not ban users in this guild since you do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return false;
        }

        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I can not ban users in this guild since I do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have BAN_MEMBERS permission on the server '{}' ",
                    guild.getName());
            return false;
        }
        return true;
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    private static void banUser(@NotNull User target, @NotNull Member author,
            @NotNull String reason, int deleteHistoryDays, @NotNull Guild guild,
            @NotNull SlashCommandEvent event) {
        event.getJDA()
            .openPrivateChannelById(target.getIdLong())
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been banned from the server %s.
                            If you think this was a mistake, please contact a moderator or admin of the server.
                            The reason for the ban is: %s
                            """
                        .formatted(guild.getName(), reason)))
            .mapToResult()
            .flatMap(result -> guild.ban(target, deleteHistoryDays, reason))
            .flatMap(result -> event.reply("'%s' was banned by '%s' for: %s"
                .formatted(target.getAsTag(), author.getUser().getAsTag(), reason)))
            .queue();

        logger.info(
                "'{}' ({}) banned the user '{}' ({}) from guild '{}' and deleted their message history of the last {} days, for reason '{}'",
                author.getUser().getAsTag(), author.getId(), target.getAsTag(), target.getId(),
                guild.getName(), deleteHistoryDays, reason);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null");
        Member target = targetOption.getAsMember();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        // Member doesn't exist if attempting to ban a user who is not part of the guild.
        if (target != null && !handleCanInteractWithTarget(bot, author, target, event)) {
            return;
        }
        if (!handleHasPermissions(bot, author, guild, event)) {
            return;
        }
        if (!ModerationUtils.handleReason(reason, event)) {
            return;
        }

        int deleteHistoryDays = Math
            .toIntExact(Objects.requireNonNull(event.getOption(DELETE_HISTORY_OPTION)).getAsLong());
        banUser(targetOption.getAsUser(), author, reason, deleteHistoryDays, guild, event);
    }
}
