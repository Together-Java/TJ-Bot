package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Due to a technical limitation in Discord, roles with more than 100 users can not be ghost-pinged
 * into helper threads.
 * <p>
 * This routine mitigates the problem by automatically pruning inactive users from helper roles
 * approaching this limit.
 */
public final class AutoPruneHelperRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(AutoPruneHelperRoutine.class);

    private static final int ROLE_FULL_LIMIT = 100;
    private static final int ROLE_FULL_THRESHOLD = 95;
    private static final int PRUNE_MEMBER_AMOUNT = 7;
    private static final Period INACTIVE_AFTER = Period.ofDays(90);
    private static final int RECENTLY_JOINED_DAYS = 4;

    private final HelpSystemHelper helper;
    private final ModAuditLogWriter modAuditLogWriter;
    private final Database database;
    private final List<String> allCategories;

    /**
     * Creates a new instance.
     *
     * @param config to determine all helper categories
     * @param helper the helper to use
     * @param modAuditLogWriter to inform mods when manual pruning becomes necessary
     * @param database to determine whether an user is inactive
     */
    public AutoPruneHelperRoutine(Config config, HelpSystemHelper helper,
            ModAuditLogWriter modAuditLogWriter, Database database) {
        allCategories = config.getHelpSystem().getCategories();
        this.helper = helper;
        this.modAuditLogWriter = modAuditLogWriter;
        this.database = database;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuildCache().forEach(this::pruneForGuild);
    }

    private void pruneForGuild(Guild guild) {
        ForumChannel helpForum = guild.getForumChannels()
            .stream()
            .filter(channel -> helper.isHelpForumName(channel.getName()))
            .findAny()
            .orElseThrow();
        Instant now = Instant.now();

        allCategories.stream()
            .map(category -> helper.handleFindRoleForCategory(category, guild))
            .filter(Optional::isPresent)
            .map(Optional::orElseThrow)
            .forEach(role -> pruneRoleIfFull(role, helpForum, now));
    }

    private void pruneRoleIfFull(Role role, ForumChannel helpForum, Instant when) {
        role.getGuild().findMembersWithRoles(role).onSuccess(members -> {
            if (isRoleFull(members)) {
                logger.debug("Helper role {} is full, starting to prune.", role.getName());
                pruneRole(role, members, helpForum, when);
            }
        });
    }

    private boolean isRoleFull(Collection<?> members) {
        return members.size() >= ROLE_FULL_THRESHOLD;
    }

    private void pruneRole(Role role, List<? extends Member> members, ForumChannel helpForum,
            Instant when) {
        List<Member> membersShuffled = new ArrayList<>(members);
        Collections.shuffle(membersShuffled);

        List<Member> membersToPrune = membersShuffled.stream()
            .filter(member -> isMemberInactive(member, when))
            .limit(PRUNE_MEMBER_AMOUNT)
            .toList();
        if (membersToPrune.size() < PRUNE_MEMBER_AMOUNT) {
            warnModsAbout(
                    "Attempting to prune helpers from role **%s** (%d members), but only found %d inactive users. That is less than expected, the category might eventually grow beyond the limit."
                        .formatted(role.getName(), members.size(), membersToPrune.size()),
                    role.getGuild());
        }
        if (members.size() - membersToPrune.size() >= ROLE_FULL_LIMIT) {
            warnModsAbout(
                    "The helper role **%s** went beyond its member limit (%d), despite automatic pruning. It will not function correctly anymore. Please manually prune some users."
                        .formatted(role.getName(), ROLE_FULL_LIMIT),
                    role.getGuild());
        }

        logger.info("Pruning {} users {} from role {}", membersToPrune.size(), membersToPrune,
                role.getName());
        membersToPrune.forEach(member -> pruneMemberFromRole(member, role, helpForum));
    }

    private boolean isMemberInactive(Member member, Instant when) {
        if (member.hasTimeJoined()) {
            Instant memberJoined = member.getTimeJoined().toInstant();
            if (Duration.between(memberJoined, when).toDays() <= RECENTLY_JOINED_DAYS) {
                // New users are protected from purging to not immediately kick them out of the role
                // again
                return false;
            }
        }

        Instant latestActiveMoment = when.minus(INACTIVE_AFTER);

        // Has no recent help message
        return database.read(context -> context.fetchCount(HELP_CHANNEL_MESSAGES,
                HELP_CHANNEL_MESSAGES.GUILD_ID.eq(member.getGuild().getIdLong())
                    .and(HELP_CHANNEL_MESSAGES.AUTHOR_ID.eq(member.getIdLong()))
                    .and(HELP_CHANNEL_MESSAGES.SENT_AT.greaterThan(latestActiveMoment)))) == 0;
    }

    private void pruneMemberFromRole(Member member, Role role, ForumChannel helpForum) {
        Guild guild = member.getGuild();

        String dmMessage =
                """
                        You seem to have been inactive for some time in server **%s**, hence we removed you from the **%s** role.
                        If that was a mistake, just head back to %s and select the role again.
                        Sorry for any inconvenience caused by this 🙇"""
                    .formatted(guild.getName(), role.getName(), helpForum.getAsMention());

        guild.removeRoleFromMember(member, role)
            .flatMap(any -> member.getUser().openPrivateChannel())
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .queue();
    }

    private void warnModsAbout(String message, Guild guild) {
        logger.warn(message);

        modAuditLogWriter.write("Auto-prune helpers", message, null, Instant.now(), guild);
    }
}
