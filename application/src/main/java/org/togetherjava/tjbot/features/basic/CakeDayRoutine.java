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

public class CakeDayRoutine implements Routine {

    private static final Logger logger = LoggerFactory.getLogger(CakeDayRoutine.class);
    private static final DateTimeFormatter MONTH_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd");
    private static final int BULK_INSERT_SIZE = 500;
    private final Predicate<String> cakeDayRolePredicate;
    private final CakeDayConfig config;
    private final Database database;

    public CakeDayRoutine(Config config, Database database) {
        this.config = config.getCakeDayConfig();
        this.database = database;

        this.cakeDayRolePredicate = Pattern.compile(this.config.rolePattern()).asPredicate();
    }

    /**
     * Retrieves the schedule of this routine. Called by the core system once during the startup in
     * order to execute the routine accordingly.
     * <p>
     * Changes on the schedule returned by this method afterwards will not be picked up.
     *
     * @return the schedule of this routine
     */
    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.DAYS);
    }

    /**
     * Triggered by the core system on the schedule defined by {@link #createSchedule()}.
     *
     * @param jda the JDA instance the bot is operating with
     */
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

    private CompletableFuture<Void> removeMembersCakeDayRole(Role cakeDayRole, Guild guild) {
        return CompletableFuture.runAsync(() -> guild.findMembersWithRoles(cakeDayRole)
            .onSuccess(members -> removeRoleFromMembers(guild, cakeDayRole, members)));
    }

    private void removeRoleFromMembers(Guild guild, Role role, List<Member> members) {
        members.forEach(member -> {
            UserSnowflake snowflake = UserSnowflake.fromId(member.getIdLong());
            guild.removeRoleFromMember(snowflake, role).complete();
        });
    }

    private int getCakeDayCount(Database database) {
        return database.read(context -> context.fetchCount(CAKE_DAYS));
    }

    private void populateAllGuildCakeDays(JDA jda) {
        jda.getGuilds().forEach(this::batchPopulateGuildCakeDays);
    }

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

    private Optional<Role> getCakeDayRoleFromGuild(Guild guild) {
        return guild.getRoles()
            .stream()
            .filter(role -> cakeDayRolePredicate.test(role.getName()))
            .findFirst();
    }

    private List<CakeDaysRecord> findCakeDaysTodayFromDatabase() {
        String todayMonthDay = OffsetDateTime.now().format(MONTH_DAY_FORMATTER);

        return database
            .read(context -> context.selectFrom(CAKE_DAYS)
                .where(CAKE_DAYS.JOINED_MONTH_DAY.eq(todayMonthDay))
                .fetch())
            .collect(Collectors.toList());
    }
}
