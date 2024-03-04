package org.togetherjava.tjbot.features.cakeday;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.togetherjava.tjbot.db.generated.tables.CakeDays.CAKE_DAYS;

/**
 * Service for managing the Cake Day feature.
 */
public class CakeDayService {
    private static final Logger logger = LoggerFactory.getLogger(CakeDayService.class);
    private static final DateTimeFormatter MONTH_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd");
    private final Set<String> cakeDaysCache = new HashSet<>();
    private final Predicate<String> cakeDayRolePredicate;
    private final CakeDayConfig config;
    private final Database database;

    /**
     * Constructs a {@link CakeDayService} with the given configuration and database.
     *
     * @param config the configuration for cake day management
     * @param database the database for storing cake day information
     */
    public CakeDayService(Config config, Database database) {
        this.config = config.getCakeDayConfig();
        this.database = database;

        this.cakeDayRolePredicate = Pattern.compile(this.config.rolePattern()).asPredicate();
    }

    private Optional<Role> getCakeDayRole(Guild guild) {
        Role cakeDayRole = getCakeDayRoleFromGuild(guild).orElse(null);

        if (cakeDayRole == null) {
            logger.warn("Cake day role with pattern {} not found for guild: {}",
                    config.rolePattern(), guild.getName());
            return Optional.empty();
        }

        return Optional.of(cakeDayRole);
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
        Role cakeDayRole = getCakeDayRole(guild).orElse(null);

        if (cakeDayRole == null) {
            return;
        }

        refreshMembersCakeDayRoles(cakeDayRole, guild);
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
            addTodayMembersCakeDayRole(guild);
        });
    }

    /**
     * Asynchronously adds the specified cake day role to guild members who are celebrating their
     * cake day today.
     * <p>
     * The cake day role is added to members who have been in the guild for at least one year.
     *
     * @param guild the guild in which to add the cake day role to members
     */
    private void addTodayMembersCakeDayRole(Guild guild) {
        findCakeDaysTodayFromDatabase().forEach(cakeDayRecord -> {
            UserSnowflake userSnowflake = UserSnowflake.fromId(cakeDayRecord.getUserId());

            int anniversary = OffsetDateTime.now().getYear() - cakeDayRecord.getJoinedYear();
            if (anniversary > 0) {
                addCakeDayRole(userSnowflake, guild);
            }
        });
    }


    /**
     * Adds the cake day role to the specified user in the given guild, if available.
     *
     * @param snowflake the snowflake ID of the user to whom the cake day role will be added
     * @param guild the guild in which the cake day role will be added to the user
     */
    protected void addCakeDayRole(UserSnowflake snowflake, Guild guild) {
        Role cakeDayRole = getCakeDayRole(guild).orElse(null);

        if (cakeDayRole == null) {
            return;
        }

        guild.addRoleToMember(snowflake, cakeDayRole).complete();
    }

    /**
     * Adds the cake day role to the specified member if the cake day role exists in the guild.
     *
     * @param member the {@link Member} to whom the cake day role will be added
     */
    protected void addCakeDayRole(Member member) {
        Guild guild = member.getGuild();
        UserSnowflake snowflake = UserSnowflake.fromId(member.getId());
        Role cakeDayRole = getCakeDayRole(guild).orElse(null);

        if (cakeDayRole == null) {
            return;
        }

        guild.addRoleToMember(snowflake, cakeDayRole).complete();
    }

    /**
     * Removes a specified role from a list of members in a guild.
     *
     * @param guild the guild from which to remove the role from members
     * @param role the role to be removed from the members
     * @param members the list of members from which the role will be removed
     */
    private void removeRoleFromMembers(Guild guild, Role role, List<Member> members) {
        members.forEach(member -> {
            UserSnowflake snowflake = UserSnowflake.fromId(member.getIdLong());
            guild.removeRoleFromMember(snowflake, role).complete();
        });
    }

    /**
     * Creates a query to insert a member's cake day information into the database.
     *
     * @param member the member whose cake day information is to be inserted
     * @param guildId the ID of the guild to which the member belongs
     * @return an Optional containing the query to insert cake day information if the member has a
     *         join time; empty Optional otherwise
     */
    private Optional<Query> createMemberCakeDayQuery(Member member, long guildId) {
        if (!member.hasTimeJoined()) {
            return Optional.empty();
        }

        OffsetDateTime cakeDay = member.getTimeJoined();
        String joinedMonthDay = cakeDay.format(MONTH_DAY_FORMATTER);

        return Optional.of(DSL.insertInto(CAKE_DAYS)
            .set(CAKE_DAYS.JOINED_MONTH_DAY, joinedMonthDay)
            .set(CAKE_DAYS.JOINED_YEAR, cakeDay.getYear())
            .set(CAKE_DAYS.GUILD_ID, guildId)
            .set(CAKE_DAYS.USER_ID, member.getIdLong()));
    }

    /**
     * Inserts the cake day of a member into the database.
     * <p>
     * If the member has no join date, nothing happens.
     *
     * @param member the member whose cake day is to be inserted into the database
     * @param guildId the ID of the guild to which the member belongs
     */
    protected void insertMemberCakeDayToDatabase(Member member, long guildId) {
        Query insertQuery = createMemberCakeDayQuery(member, guildId).orElse(null);

        if (insertQuery == null) {
            logger.warn("Tried to add member {} to database but found no time joined",
                    member.getId());
        }

        database.write(context -> context.batch(insertQuery).execute());
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
        return guild.getRoles()
            .stream()
            .filter(role -> cakeDayRolePredicate.test(role.getName()))
            .findFirst();
    }

    /**
     * Removes the cake day information of the specified user from the database and clears the cache
     * for the guild.
     *
     * @param user the {@link User} who left the guild
     * @param guild the {@link Guild} from which the user left
     */
    protected void handleUserLeft(User user, Guild guild) {
        removeMemberCakeDayFromDatabase(user.getIdLong(), guild.getIdLong());
        cakeDaysCache.remove(guild.getId());
    }

    /**
     * Finds cake days records for today from the database.
     *
     * @return a list of {@link CakeDaysRecord} objects representing cake days for today
     */
    private List<CakeDaysRecord> findCakeDaysTodayFromDatabase() {
        String todayMonthDay = OffsetDateTime.now().format(MONTH_DAY_FORMATTER);

        return database
            .read(context -> context.selectFrom(CAKE_DAYS)
                .where(CAKE_DAYS.JOINED_MONTH_DAY.eq(todayMonthDay))
                .fetch())
            .collect(Collectors.toList());
    }

    /**
     * Searches for the {@link CakeDaysRecord} of a user in the database.
     *
     * @param userId the user ID of the user whose cake day record is to be retrieved
     * @return an {@link Optional} containing the cake day record of the user, or an empty
     *         {@link Optional} if no record is found
     */
    protected Optional<CakeDaysRecord> findUserCakeDayFromDatabase(long userId) {
        return database
            .read(context -> context.selectFrom(CAKE_DAYS)
                .where(CAKE_DAYS.USER_ID.eq(userId))
                .fetch())
            .collect(Collectors.toList())
            .stream()
            .findFirst();
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

        return now.getMonth() == joinMonthDate.getMonth()
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
