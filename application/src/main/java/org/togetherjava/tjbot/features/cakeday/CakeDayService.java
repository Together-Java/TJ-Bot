package org.togetherjava.tjbot.features.cakeday;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.CakeDayConfig;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.CakeDaysRecord;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.togetherjava.tjbot.db.generated.tables.CakeDays.CAKE_DAYS;

/**
 * Service for managing the Cake Day feature.
 */
public class CakeDayService {
    private static final Logger logger = LoggerFactory.getLogger(CakeDayService.class);
    private static final DateTimeFormatter MONTH_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd");
    private final Set<String> cakeDaysCache = new HashSet<>();
    private final String cakeDayRolePattern;
    private final CakeDayConfig fullConfig;
    private final Database database;

    /**
     * Constructs a {@link CakeDayService} with the given configuration and database.
     *
     * @param fullConfig the full configuration for cake day management
     * @param database the database for storing cake day information
     */
    public CakeDayService(Config fullConfig, Database database) {
        this.fullConfig = fullConfig.getCakeDayConfig();
        this.database = database;

        this.cakeDayRolePattern = this.fullConfig.rolePattern();
    }

    private Optional<Role> getCakeDayRole(Guild guild) {
        Optional<Role> cakeDayRole = getCakeDayRoleFromGuild(guild);

        if (cakeDayRole.isEmpty()) {
            logger.warn("Cake day role with pattern {} not found for guild: {}",
                    fullConfig.rolePattern(), guild.getName());
        }

        return cakeDayRole;
    }

    /**
     * Reassigns the cake day role for all members of the given guild.
     * <p>
     * If the cake day role is not found based on the configured pattern, a warning message is
     * logged, and no action is taken.
     *
     * @param guild the guild for which to reassign the cake day role
     */
    protected void reassignCakeDayRole(Guild guild) {
        getCakeDayRole(guild).ifPresent(role -> refreshMembersCakeDayRoles(role, guild));
    }

    /**
     * Refreshes the Cake Day roles for members in the specified guild.
     *
     * @param cakeDayRole the Cake Day role to refresh
     * @param guild the guild in which to refresh Cake Day roles
     */
    private void refreshMembersCakeDayRoles(Role cakeDayRole, Guild guild) {
        guild.findMembersWithRoles(cakeDayRole).onSuccess(members -> {
            removeRoleFromMembers(guild, cakeDayRole, members);
            addTodayMembersCakeDayRole(guild, cakeDayRole);
        });
    }

    /**
     * Assigns a special role to members whose cake day (anniversary of joining) is today, but only
     * if they have been a member for at least one year.
     * <p>
     * This method checks the current date against the cake day records in the database for each
     * member of the given guild. If the member's cake day is today, and they have been a member for
     * at least one year, the method assigns them a special role.
     *
     * @param guild the guild to check for members celebrating their cake day today
     */
    private void addTodayMembersCakeDayRole(Guild guild, Role cakeDayRole) {
        findCakeDaysTodayFromDatabase(guild).forEach(cakeDayRecord -> {
            Member member = guild.getMemberById(cakeDayRecord.getUserId());

            if (member == null) {
                return;
            }

            if (!hasMemberCakeDayToday(member)) {
                return;
            }

            addCakeDayRole(member, cakeDayRole);
        });
    }

    /**
     * Adds the cake day role supplied to the specified member if the cake day role exists in the
     * guild.
     *
     * @param member the {@link Member} to whom the cake day role will be added
     */
    protected void addCakeDayRole(Member member) {
        Guild guild = member.getGuild();
        Optional<Role> cakeDayRole = getCakeDayRole(guild);

        if (cakeDayRole.isEmpty()) {
            return;
        }

        addCakeDayRole(member, cakeDayRole.get());
    }

    /**
     * Adds the cake day role supplied to the specified member.
     *
     * @param member the {@link Member} to whom the cake day role will be added
     * @param cakeDayRole the cake day {@link Role}
     */
    private void addCakeDayRole(Member member, @NotNull Role cakeDayRole) {
        Guild guild = member.getGuild();

        guild.addRoleToMember(member, cakeDayRole).queue();
    }

    /**
     * Removes a specified role from a list of members in a {@link Guild}.
     *
     * @param guild the {@link Guild} from which to remove the role from members
     * @param role the {@link Role} to be removed from the members
     * @param members the {@link List} of members from which the {@link Role} will be removed
     */
    private void removeRoleFromMembers(Guild guild, Role role, List<Member> members) {
        members.forEach(member -> guild.removeRoleFromMember(member, role)
            .queue(null,
                    failure -> logger.error("Could not remove role {} from {} ({}): {}",
                            role.getName(), member.getEffectiveName(), member.getId(),
                            failure.getMessage())));
    }

    /**
     * Asynchronously inserts a member's cake day information into the database.
     *
     * <p>
     * This method retrieves the {@link Member} from the Discord API to obtain the member's join
     * timestamp, derives the month/day portion using {@code MONTH_DAY_FORMATTER}, and then inserts
     * a row into {@code CAKE_DAYS} with the join month-day, join year, guild id, and user id.
     *
     * <p>
     * The Discord retrieval is performed asynchronously; on success the database write is executed.
     * If the Discord retrieval fails (e.g., member not found, missing permissions, network issues),
     * the insert is skipped and a warning is logged.
     *
     * @param member the member whose cake day information should be inserted
     * @param guildId the id of the guild associated with the cake day record
     */
    protected void insertMemberCakeDayToDatabase(Member member, long guildId) {
        member.getGuild().retrieveMemberById(member.getIdLong()).queue(retrievedMember -> {
            OffsetDateTime timeJoined = retrievedMember.getTimeJoined();
            String joinedMonthDay = timeJoined.format(MONTH_DAY_FORMATTER);

            Query query = DSL.insertInto(CAKE_DAYS)
                .set(CAKE_DAYS.JOINED_MONTH_DAY, joinedMonthDay)
                .set(CAKE_DAYS.JOINED_YEAR, timeJoined.getYear())
                .set(CAKE_DAYS.GUILD_ID, guildId)
                .set(CAKE_DAYS.USER_ID, member.getIdLong());

            database.write(ctx -> ctx.execute(query));
        }, failure -> logger.warn("Could not insert member cake day into the database: {}",
                failure.getMessage()));
    }

    /**
     * Removes the member's cake day record from the database.
     *
     * @param userId the ID of the user whose cake day information is to be removed
     * @param guildId the ID of the guild where the user belongs
     */
    protected void removeMemberCakeDayFromDatabase(long userId, long guildId) {
        database.write(context -> context.deleteFrom(CAKE_DAYS)
            .where(CAKE_DAYS.USER_ID.eq(userId))
            .and(CAKE_DAYS.GUILD_ID.eq(guildId))
            .execute());
    }

    /**
     * Retrieves the cake day {@link Role} from the specified guild.
     *
     * @param guild the {@link Guild} from which to retrieve the cake day role
     * @return an {@link Optional} containing the cake day role if found, otherwise empty
     */
    private Optional<Role> getCakeDayRoleFromGuild(Guild guild) {
        return guild.getRolesByName(cakeDayRolePattern, true).stream().findFirst();
    }

    /**
     * Removes the cake day information of the specified user from the database and clears the cache
     * for the guild.
     *
     * @param user the {@link User} who left the guild
     * @param guild the {@link Guild} from which the user left
     */
    protected void removeUserCakeDay(User user, Guild guild) {
        removeMemberCakeDayFromDatabase(user.getIdLong(), guild.getIdLong());
        cakeDaysCache.remove(guild.getId());
    }

    /**
     * Finds cake days records for today from the database.
     *
     * @return a list of {@link CakeDaysRecord} objects representing cake days for today
     */
    private List<CakeDaysRecord> findCakeDaysTodayFromDatabase(Guild guild) {
        String todayMonthDay = OffsetDateTime.now().format(MONTH_DAY_FORMATTER);

        return database.read(context -> context.selectFrom(CAKE_DAYS)
            .where(CAKE_DAYS.JOINED_MONTH_DAY.eq(todayMonthDay))
            .and(CAKE_DAYS.GUILD_ID.eq(guild.getIdLong()))
            .fetch());
    }

    /**
     * Searches for the {@link CakeDaysRecord} of a user in the database.
     *
     * @param userId the user ID of the user whose cake day record is to be retrieved
     * @return an {@link Optional} containing the cake day record of the user, or an empty
     *         {@link Optional} if no record is found
     */
    protected Optional<CakeDaysRecord> findUserCakeDayFromDatabase(long userId) {
        return database.read(ctx -> Optional
            .ofNullable(ctx.selectFrom(CAKE_DAYS).where(CAKE_DAYS.USER_ID.eq(userId)).fetchOne()));
    }

    /**
     * Checks if the provided user is cached in the cake day stores cache.
     *
     * @param user the user to check if cached
     * @return true if the user is cached, false otherwise
     */
    protected boolean isUserCached(User user) {
        return cakeDaysCache.contains(user.getId());
    }


    /**
     * Checks if the provided {@link Member} has their "cake day" today.
     * 
     * @param member the {@link Member} whose cake day is to be checked
     * @return true if the member has their cake day today; otherwise, false
     */
    protected boolean hasMemberCakeDayToday(Member member) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime joinMonthDate = member.getTimeJoined();
        int anniversary = now.getYear() - joinMonthDate.getYear();

        return anniversary > 0 && now.getMonth() == joinMonthDate.getMonth()
                && now.getDayOfMonth() == joinMonthDate.getDayOfMonth();
    }

    /**
     * Adds the provided user to the cake day stores cache.
     *
     * @param user the user to add to the cache
     */
    protected void addToCache(User user) {
        cakeDaysCache.add(user.getId());
    }
}
