package org.togetherjava.tjbot.features.cakeday;

import net.dv8tion.jda.api.JDA;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.features.Routine;

import java.util.concurrent.TimeUnit;

/**
 * Represents a routine for managing cake day celebrations.
 * <p>
 * This routine handles the assignment and removal of a designated cake day role to guild members
 * based on their anniversary of joining the guild.
 */
public class CakeDayRoutine implements Routine {

    private final CakeDayService cakeDayService;

    /**
     * Constructs a new {@link CakeDayRoutine} instance.
     *
     * @param cakeDayService an instance of the cake day service
     */
    public CakeDayRoutine(CakeDayService cakeDayService) {
        this.cakeDayService = cakeDayService;
    }

    @Override
    @NotNull
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.DAYS);
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        jda.getGuilds().forEach(cakeDayService::reassignCakeDayRole);
    }
}
