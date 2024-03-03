package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.CakeDayConfig;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.CakeDaysRecord;
import org.togetherjava.tjbot.features.Routine;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.togetherjava.tjbot.db.generated.tables.CakeDays.CAKE_DAYS;

/**
 * Represents a routine for managing cake day celebrations.
 * <p>
 * This routine handles the assignment and removal of a designated cake day role to guild members
 * based on their anniversary of joining the guild.
 */
public class CakeDayRoutine implements Routine {

    private static final Logger logger = LoggerFactory.getLogger(CakeDayRoutine.class);
    private static final DateTimeFormatter MONTH_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd");
    private static final int BULK_INSERT_SIZE = 500;
    private final Predicate<String> cakeDayRolePredicate;
    private final CakeDayConfig config;
    private final Database database;

    /**
     * Constructs a new {@link CakeDayRoutine} instance.
     *
     * @param config the configuration for cake day routines
     * @param database the database for accessing cake day data
     */
    public CakeDayRoutine(Config config, Database database) {
        this.config = config.getCakeDayConfig();
        this.database = database;

        this.cakeDayRolePredicate = Pattern.compile(this.config.rolePattern()).asPredicate();
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.DAYS);
    }

    @Override
    public void runRoutine(JDA jda) {
        if (getCakeDayCount(this.database) == 0) {
            int guildsCount = jda.getGuilds().size();

            logger.info("Found empty cake_days table. Populating from guild count: {}",
                    guildsCount);
            CompletableFuture.runAsync(() -> populateAllGuildCakeDays(jda))
                .handle((result, exception) -> {
                    if (exception != null) {
                        logger.error("populateAllGuildCakeDays failed. Message: {}",
                                exception.getMessage());
                    } else {
                        logger.info("populateAllGuildCakeDays completed.");
                    }

                    return result;
                });

            return;
        }

        jda.getGuilds().forEach(this::reassignCakeDayRole);
    }

    /**
     * Reassigns the cake day role for all members of the given guild.
     * <p>
     * If the cake day role is not found based on the configured pattern, a warning message is
     * logged, and no action is taken.
     *
     * @param guild the guild for which to reassign the cake day role
     */
    private void reassignCakeDayRole(Guild guild) {
        Role cakeDayRole = getCakeDayRoleFromGuild(guild).orElse(null);

        if (cakeDayRole == null) {
            logger.warn("Cake day role with pattern {} not found for guild: {}",
                    config.rolePattern(), guild.getName());
            return;
        }

        removeMembersCakeDayRole(cakeDayRole, guild)
            .thenCompose(result -> addTodayMembersCakeDayRole(cakeDayRole, guild))
            .join();
    }

    /**
     * Asynchronously adds the specified cake day role to guild members who are celebrating their
     * cake day today.
     * <p>
     * The cake day role is added to members who have been in the guild for at least one year.
     *
     * @param cakeDayRole the cake day role to add to qualifying members
     * @param guild the guild in which to add the cake day role to members
     * @return a {@link CompletableFuture} representing the asynchronous operation
     */
    private CompletableFuture<Void> addTodayMembersCakeDayRole(Role cakeDayRole, Guild guild) {
        return CompletableFuture
            .runAsync(() -> findCakeDaysTodayFromDatabase().forEach(cakeDayRecord -> {
                UserSnowflake snowflake = UserSnowflake.fromId(cakeDayRecord.getUserId());

                int anniversary = OffsetDateTime.now().getYear() - cakeDayRecord.getJoinedYear();
                if (anniversary > 0) {
                    guild.addRoleToMember(snowflake, cakeDayRole).complete();
                }
            }));
    }

    /**
     * Removes the specified cake day role from all members who possess it in the given guild
     * asynchronously.
     *
     * @param cakeDayRole the cake day role to be removed from members
     * @param guild the guild from which to remove the cake day role
     * @return a {@link CompletableFuture} representing the asynchronous operation
     */
    private CompletableFuture<Void> removeMembersCakeDayRole(Role cakeDayRole, Guild guild) {
        return CompletableFuture.runAsync(() -> guild.findMembersWithRoles(cakeDayRole)
            .onSuccess(members -> removeRoleFromMembers(guild, cakeDayRole, members)));
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
     * Retrieves the count of cake days from the provided database.
     * <p>
     * This uses the table <b>cake_days</b> to find the answer.
     *
     * @param database the database from which to retrieve the count of cake days
     * @return the count of cake days stored in the database
     */
    private int getCakeDayCount(Database database) {
        return database.read(context -> context.fetchCount(CAKE_DAYS));
    }

    /**
     * Populates cake days for all guilds in the provided JDA instance.
     * <p>
     * This method iterates through all guilds in the provided JDA instance and populates cake days
     * for each guild. It is primarily used for batch populating the <b>cake_days</b> table once it
     * is found to be empty.
     *
     * @param jda the JDA instance containing the guilds to populate cake days for
     */
    private void populateAllGuildCakeDays(JDA jda) {
        jda.getGuilds().forEach(this::batchPopulateGuildCakeDays);
    }

    /**
     * Batch populates guild cake days for the given guild.
     * <p>
     * Uses a buffer for all the queries it makes and its size is determined by the
     * {@code BULK_INSERT_SIZE} option.
     *
     * @param guild the guild for which to populate cake days
     */
    private void batchPopulateGuildCakeDays(Guild guild) {
        final List<Query> queriesBuffer = new ArrayList<>();

        guild.getMembers().stream().filter(Member::hasTimeJoined).forEach(member -> {
            if (queriesBuffer.size() == BULK_INSERT_SIZE) {
                database.write(context -> context.batch(queriesBuffer).execute());
                queriesBuffer.clear();
                return;
            }

            Optional<Query> query = createMemberCakeDayQuery(member, guild.getIdLong());
            query.ifPresent(queriesBuffer::add);
        });

        // Flush the queries buffer so that the remaining ones get written
        if (!queriesBuffer.isEmpty()) {
            database.write(context -> context.batch(queriesBuffer).execute());
        }
    }

    /**
     * Creates a query to insert a member's cake day information into the database.
     * <p>
     * Primarily used for manually constructing queries for members' cake days which are called from
     * {@link CakeDayRoutine#batchPopulateGuildCakeDays(Guild)} and added in a batch to be sent to
     * the database.
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
     * Retrieves the cake day {@link Role} from the specified guild.
     *
     * @param guild the guild from which to retrieve the cake day role
     * @return an optional containing the cake day role if found, otherwise empty
     */
    private Optional<Role> getCakeDayRoleFromGuild(Guild guild) {
        return guild.getRoles()
            .stream()
            .filter(role -> cakeDayRolePredicate.test(role.getName()))
            .findFirst();
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
}
