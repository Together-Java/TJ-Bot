package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.JDA;

import java.util.concurrent.TimeUnit;

/**
 * Routines are executed on a reoccurring schedule by the core system.
 * <p>
 * All routines have to implement this interface. A new routine can then be registered by adding it
 * to {@link Features}.
 * <p>
 * <p>
 * After registration, the system will automatically start and execute {@link #runRoutine(JDA)} on
 * the schedule defined by {@link #createSchedule()}.
 */
public interface Routine extends Feature {
    /**
     * Retrieves the schedule of this routine. Called by the core system once during the startup in
     * order to execute the routine accordingly.
     * <p>
     * Changes on the schedule returned by this method afterwards will not be picked up.
     *
     * @return the schedule of this routine
     */
    Schedule createSchedule();

    /**
     * Triggered by the core system on the schedule defined by {@link #createSchedule()}.
     *
     * @param jda the JDA instance the bot is operating with
     */
    void runRoutine(JDA jda);

    /**
     * The schedule of routines.
     *
     * @param mode whether subsequent executions are executed at a fixed rate or are delayed,
     *        influences how {@link #duration} is interpreted
     * @param initialDuration the time which the first execution of the routine is delayed
     * @param duration the time all subsequent executions of the routine are delayed. Either
     *        measured before execution ({@link ScheduleMode#FIXED_RATE}) or after execution has
     *        finished ({@link ScheduleMode#FIXED_DELAY}).
     * @param unit the time unit for both, {@link #initialDuration} and {@link #duration}, e.g.
     *        seconds
     */
    record Schedule(ScheduleMode mode, long initialDuration, long duration, TimeUnit unit) {
    }

    /**
     * Whether subsequent executions of a routine are executed at a fixed rate or are delayed.
     */
    enum ScheduleMode {
        /**
         * Executions are scheduled for a fixed rate, the time duration between executions is
         * measured between their starting time.
         */
        FIXED_RATE,
        /**
         * Executions are scheduled for a fixed delay, the time duration between executions is
         * measured between after they have finished.
         */
        FIXED_DELAY
    }
}
