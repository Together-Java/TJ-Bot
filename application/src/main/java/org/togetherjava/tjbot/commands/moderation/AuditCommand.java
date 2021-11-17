package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.BanSystem;
import org.togetherjava.tjbot.db.generated.tables.WarnSystem;
import org.togetherjava.tjbot.db.generated.tables.records.BanSystemRecord;
import org.togetherjava.tjbot.db.generated.tables.records.WarnSystemRecord;

import java.awt.*;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.commands.utils.MessageUtils.replyEphemeral;

//TODO add javadoc
public class AuditCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(AuditCommand.class);
    private static final String ACTION_VERB = "audit";
    private static final String COMMAND_WARN = "warn";
    private static final String WARN_USER_OPTION = "user";
    private static final String COMMAND_KICK = "kick";
    private static final String KICK_USER_OPTION = "user";
    private static final String COMMAND_BAN = "ban";
    private static final String BAN_USER_OPTION = "user";
    private static final String DESCRIPTION = "The user who you want to get the values for";
    private static final String USER_IS_NULL = "The user is null";
    private final Database database;
    private final Predicate<String> hasRequiredRole;

    /**
     * Creates a new adapter with the given data.
     *
     * @param database used to retrieve the user data for the moderation commands
     */
    public AuditCommand(Database database) {
        super(ACTION_VERB, "get the audit for the commands", SlashCommandVisibility.GUILD);
        this.database = database;

        getData().addSubcommands(
                new SubcommandData(COMMAND_WARN, "Gets the warn values for that user")
                    .addOption(OptionType.USER, WARN_USER_OPTION, DESCRIPTION, true),
                new SubcommandData(COMMAND_KICK, "Gets the kick values for that user")
                    .addOption(OptionType.USER, KICK_USER_OPTION, DESCRIPTION, true),
                new SubcommandData(COMMAND_BAN, "Gets the ban values for that user")
                    .addOption(OptionType.USER, BAN_USER_OPTION, DESCRIPTION, true));

        hasRequiredRole = Pattern.compile(Config.getInstance().getSoftModerationRolePattern())
            .asMatchPredicate();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case COMMAND_WARN -> handleWarnCommand(event);
            case COMMAND_KICK -> handleKickCommand(event);
            case COMMAND_BAN -> handleBanCommand(event);
            default -> throw new AssertionError();
        }
    }

    private static boolean noValueFound(Optional<?> value, @NotNull User target, String commandName,
            @NotNull CommandInteraction event) {
        if (value.isEmpty()) {
            event
                .replyEmbeds(new EmbedBuilder().setTitle("Null")
                    .setDescription(
                            "The user " + target.getAsTag() + " has never been " + commandName)
                    .setColor(Color.decode("#895FE8"))
                    .build())
                .setEphemeral(true)
                .queue();
            return false;
        }
        return true;
    }

    //TODO add javadoc
    private void handleWarnCommand(@NotNull CommandInteraction event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(WARN_USER_OPTION), USER_IS_NULL);
        User target = userOption.getAsUser();
        Guild guild = Objects.requireNonNull(event.getGuild());

        Member bot = guild.getSelfMember();
        Member author = Objects.requireNonNull(event.getMember(), USER_IS_NULL);

        if (!handleChecks(bot, author, guild, event)) {
            return;
        }

        Long guildId = guild.getIdLong();
        Long userId = target.getIdLong();
        Optional<Integer> amountOfWarns = database.read(context -> {
            try (var select = context.selectFrom(WarnSystem.WARN_SYSTEM)) {
                return Optional
                    .ofNullable(select.where(WarnSystem.WARN_SYSTEM.USERID.eq(userId)
                        .and(WarnSystem.WARN_SYSTEM.GUILD_ID.eq(guildId))).fetchOne())
                    .map(WarnSystemRecord::getWarningAmount);
            }
        });

        Optional<String> warnReason = database.read(context -> {
            try (var select = context.selectFrom(WarnSystem.WARN_SYSTEM)) {
                return Optional
                    .ofNullable(select.where(WarnSystem.WARN_SYSTEM.USERID.eq(userId)
                        .and(WarnSystem.WARN_SYSTEM.GUILD_ID.eq(guildId))).fetchOne())
                    .map(WarnSystemRecord::getWarnReason);
            }
        });

        String warned = "warned";
        try {
            Optional<Boolean> isWarned = database.read(context -> {
                try (var select = context.selectFrom(WarnSystem.WARN_SYSTEM)) {
                    return Optional
                        .ofNullable(
                                select
                                    .where(WarnSystem.WARN_SYSTEM.USERID.eq(userId)
                                        .and(WarnSystem.WARN_SYSTEM.GUILD_ID.eq(guildId)))
                                    .fetchOne())
                        .map(WarnSystemRecord::getIsWarned);
                }
            });
            if (!noValueFound(isWarned, target, warned, event)
                    && !noValueFound(amountOfWarns, target, warned, event)
                    && !noValueFound(warnReason, target, warned, event)) {
                return;
            }

            event
                .replyEmbeds(new EmbedBuilder().setTitle("Null")
                    .setDescription("The user " + target.getAsTag() + " has  " + amountOfWarns
                            + "for the reasons " + warnReason)
                    .setColor(Color.decode("#895FE8"))
                    .build())
                .setEphemeral(true)
                .queue();
        } catch (Exception exception) {
            logger.error("Failed to check if the user is warned", exception);
            replyEphemeral("Failed to check if the user is warned", event);
        }
    }

    //TODO add javadoc
    private void handleKickCommand(@NotNull CommandInteraction event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(KICK_USER_OPTION), USER_IS_NULL);
        User target = userOption.getAsUser();
        Guild guild = Objects.requireNonNull(event.getGuild());

        Member bot = guild.getSelfMember();
        Member author = Objects.requireNonNull(event.getMember(), USER_IS_NULL);

        if (!handleChecks(bot, author, guild, event)) {
            return;
        }

        Long guildId = guild.getIdLong();
        Long userId = target.getIdLong();
    }

    //TODO add javadoc
    private void handleBanCommand(@NotNull CommandInteraction event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(BAN_USER_OPTION), USER_IS_NULL);
        User target = userOption.getAsUser();
        Guild guild = Objects.requireNonNull(event.getGuild());

        Member bot = guild.getSelfMember();
        Member author = Objects.requireNonNull(event.getMember(), USER_IS_NULL);

        if (!handleChecks(bot, author, guild, event)) {
            return;
        }

        Long guildId = guild.getIdLong();
        Long userId = target.getIdLong();
        Optional<String> banReason = database.read(context -> {
            try (var select = context.selectFrom(BanSystem.BAN_SYSTEM)) {
                return Optional
                        .ofNullable(select.where(BanSystem.BAN_SYSTEM.USERID.eq(userId)
                                .and(BanSystem.BAN_SYSTEM.GUILD_ID.eq(guildId))).fetchOne())
                        .map(BanSystemRecord::getBanReason);
            }
        });

        Optional<Long> AuthorId = database.read(context -> {
            try (var select = context.selectFrom(BanSystem.BAN_SYSTEM)) {
                return Optional
                        .ofNullable(select.where(BanSystem.BAN_SYSTEM.USERID.eq(userId)
                                .and(BanSystem.BAN_SYSTEM.GUILD_ID.eq(guildId))).fetchOne())
                        .map(BanSystemRecord::getAuthorId);
            }
        });

        String banned = "banned";
        try {
            Optional<Boolean> isBanned = database.read(context -> {
                try (var select = context.selectFrom(BanSystem.BAN_SYSTEM)) {
                    return Optional
                            .ofNullable(
                                    select
                                            .where(BanSystem.BAN_SYSTEM.USERID.eq(userId)
                                                    .and(BanSystem.BAN_SYSTEM.GUILD_ID.eq(guildId)))
                                            .fetchOne())
                            .map(BanSystemRecord::getIsBanned);
                }
            });
            if (!noValueFound(isBanned, target, banned, event)
                    && !noValueFound(banReason, target, banned, event)
                    && !noValueFound(AuthorId, target, banned, event)) {
                return;
            }

            event
                    .replyEmbeds(new EmbedBuilder().setTitle("Null")
                            .setDescription("The user " + target.getAsTag() + " was banned for the reason" + banReason
                                    + " by " + AuthorId)
                            .setColor(Color.decode("#895FE8"))
                            .build())
                    .setEphemeral(true)
                    .queue();
        } catch (Exception exception) {
            logger.error("Failed to check if the user is warned", exception);
            replyEphemeral("Failed to check if the user is warned", event);
        }
    }


    private boolean handleChecks(@NotNull Member bot, @NotNull Member author, @NotNull Guild guild,
            @NotNull Interaction event) {

        if (!ModerationUtils.handleHasAuthorRole(ACTION_VERB, hasRequiredRole, author, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasBotPermissions(ACTION_VERB, Permission.KICK_MEMBERS, bot,
                guild, event)) {
            return false;
        }
        return ModerationUtils.handleHasAuthorPermissions(ACTION_VERB, Permission.KICK_MEMBERS,
                author, guild, event);
    }
}
