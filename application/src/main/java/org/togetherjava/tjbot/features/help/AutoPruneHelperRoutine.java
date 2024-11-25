package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelperPruneConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.features.moderation.audit.ModAuditLogWriter;

import javax.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Due to a technical limitation in Discord, roles with more than 250 users cannot be ghost-pinged
 * into helper threads.
 * <p>
 * This routine mitigates the problem by automatically pruning inactive users from helper roles
 * approaching this limit.
 */
public final class AutoPruneHelperRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(AutoPruneHelperRoutine.class);

    private final int roleFullLimit;
    private final int roleFullThreshold;
    private final int pruneMemberAmount;
    private final Period inactiveAfter;
    private final int recentlyJoinedDays;

    private final HelpSystemHelper helper;
    private final ModAuditLogWriter modAuditLogWriter;
    private final Database database;
    private final List<String> allCategories;
    private final Predicate<String> selectYourRolesChannelNamePredicate;

    /**
     * Creates a new instance.
     *
     * @param config to determine all helper categories
     * @param helper the helper to use
     * @param modAuditLogWriter to inform mods when manual pruning becomes necessary
     * @param database to determine whether a user is inactive
     */
    public AutoPruneHelperRoutine(Config config, HelpSystemHelper helper,
            ModAuditLogWriter modAuditLogWriter, Database database) {
        allCategories = config.getHelpSystem().getCategories();
        this.helper = helper;
        this.modAuditLogWriter = modAuditLogWriter;
        this.database = database;

        HelperPruneConfig helperPruneConfig = config.getHelperPruneConfig();
        roleFullLimit = helperPruneConfig.roleFullLimit();
        roleFullThreshold = helperPruneConfig.roleFullThreshold();
        pruneMemberAmount = helperPruneConfig.pruneMemberAmount();
        inactiveAfter = Period.ofDays(helperPruneConfig.inactivateAfterDays());
        recentlyJoinedDays = helperPruneConfig.recentlyJoinedDays();
        selectYourRolesChannelNamePredicate =
                Pattern.compile(config.getSelectRolesChannelPattern()).asMatchPredicate();
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
        Instant now = Instant.now();
        TextChannel selectRoleChannel = getSelectRolesChannelOptional(guild.getJDA()).orElse(null);

        allCategories.stream()
            .map(category -> helper.handleFindRoleForCategory(category, guild))
            .filter(Optional::isPresent)
            .map(Optional::orElseThrow)
            .forEach(role -> pruneRoleIfFull(role, selectRoleChannel, now));
    }

    private void pruneRoleIfFull(Role role, @Nullable TextChannel selectRoleChannel, Instant when) {
        role.getGuild().findMembersWithRoles(role).onSuccess(members -> {
            if (isRoleFull(members)) {
                logger.debug("Helper role {} is full, starting to prune.", role.getName());
                pruneRole(role, members, selectRoleChannel, when);
            }
        });
    }

    private boolean isRoleFull(Collection<?> members) {
        return members.size() >= roleFullThreshold;
    }

    private void pruneRole(Role role, List<? extends Member> members,
            @Nullable TextChannel selectRoleChannel, Instant when) {
        List<Member> membersShuffled = new ArrayList<>(members);
        Collections.shuffle(membersShuffled);

        List<Member> membersToPrune = membersShuffled.stream()
            .filter(member -> isMemberInactive(member, when))
            .limit(pruneMemberAmount)
            .toList();
        if (membersToPrune.size() < pruneMemberAmount) {
            warnModsAbout(
                    "Attempting to prune helpers from role **%s** (%d members), but only found %d inactive users. That is less than expected, the category might eventually grow beyond the limit."
                        .formatted(role.getName(), members.size(), membersToPrune.size()),
                    role.getGuild());
        }
        if (members.size() - membersToPrune.size() >= roleFullLimit) {
            warnModsAbout(
                    "The helper role **%s** went beyond its member limit (%d), despite automatic pruning. It will not function correctly anymore. Please manually prune some users."
                        .formatted(role.getName(), roleFullLimit),
                    role.getGuild());
        }

        logger.info("Pruning {} users {} from role {}", membersToPrune.size(), membersToPrune,
                role.getName());
        membersToPrune.forEach(member -> pruneMemberFromRole(member, role, selectRoleChannel));
    }

    private boolean isMemberInactive(Member member, Instant when) {
        if (member.hasTimeJoined()) {
            Instant memberJoined = member.getTimeJoined().toInstant();
            if (Duration.between(memberJoined, when).toDays() <= recentlyJoinedDays) {
                // New users are protected from purging to not immediately kick them out of the role
                // again
                return false;
            }
        }

        Instant latestActiveMoment = when.minus(inactiveAfter);

        // Has no recent help message
        return database.read(context -> context.fetchCount(HELP_CHANNEL_MESSAGES,
                HELP_CHANNEL_MESSAGES.GUILD_ID.eq(member.getGuild().getIdLong())
                    .and(HELP_CHANNEL_MESSAGES.AUTHOR_ID.eq(member.getIdLong()))
                    .and(HELP_CHANNEL_MESSAGES.SENT_AT.greaterThan(latestActiveMoment)))) == 0;
    }

    private void pruneMemberFromRole(Member member, Role role,
            @Nullable TextChannel selectRoleChannel) {
        Guild guild = member.getGuild();

        String channelMentionOrFallbackMessage =
                selectRoleChannel == null ? "role selection channel"
                        : selectRoleChannel.getAsMention();

        String dmMessage =
                """
                        You seem to have been inactive for some time in server **%s**, hence we removed you from the **%s** role.
                        If that was a mistake, just head back to %s and select the role again.
                        Sorry for any inconvenience caused by this 🙇"""
                    .formatted(guild.getName(), role.getName(), channelMentionOrFallbackMessage);

        guild.removeRoleFromMember(member, role)
            .flatMap(any -> member.getUser().openPrivateChannel())
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .queue(null, failure -> logger.debug(
                    "Failed sending a DM to user ({}) while pruning them from a helper role.",
                    member.getId()));
    }

    private void warnModsAbout(String message, Guild guild) {
        logger.warn(message);

        modAuditLogWriter.write("Auto-prune helpers", message, null, Instant.now(), guild);
    }

    private Optional<TextChannel> getSelectRolesChannelOptional(JDA jda) {
        return jda.getTextChannels()
            .stream()
            .filter(textChannel -> selectYourRolesChannelNamePredicate.test(textChannel.getName()))
            .findFirst();
    }
}
