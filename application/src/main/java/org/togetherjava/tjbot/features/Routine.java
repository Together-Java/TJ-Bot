package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.JDA;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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

        private static final int HOURS_OF_DAY = 24;

        /**
         * Creates a schedule for execution at a fixed hour of the day. The initial first execution
         * will be delayed to the next fixed time that matches the given hour of the day,
         * effectively making execution stable at that fixed hour - regardless of when this method
         * was originally triggered.
         * <p>
         * For example, if the given hour is 12 o'clock, this leads to the fixed execution times of
         * only 12:00 each day. The first execution is then delayed to the closest time in that
         * schedule. For example, if triggered at 7:00, execution will happen at 12:00 and then
         * follow the schedule.
         * <p>
         * Execution will also correctly roll over to the next day, for example if the method is
         * triggered at 21:30, the next execution will be at 12:00 the following day.
         *
         * @param hourOfDay the hour of the day that marks the start of this period
         * @return the according schedule representing the planned execution
         */
        public static Schedule atFixedHour(int hourOfDay) {
            return atFixedRateFromNextFixedTime(hourOfDay, HOURS_OF_DAY);
        }

        /**
         * Creates a schedule for execution at a fixed rate (see
         * {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}).
         * The initial first execution will be delayed to the next fixed time that matches the given
         * period, effectively making execution stable at fixed times of a day - regardless of when
         * this method was originally triggered.
         * <p>
         * For example, if the given period is 8 hours with a start hour of 4 o'clock, this leads to
         * the fixed execution times of 4:00, 12:00 and 20:00 each day. The first execution is then
         * delayed to the closest time in that schedule. For example, if triggered at 7:00,
         * execution will happen at 12:00 and then follow the schedule.
         * <p>
         * Execution will also correctly roll over to the next day, for example if the method is
         * triggered at 21:30, the next execution will be at 4:00 the following day.
         *
         * @param periodStartHour the hour of the day that marks the start of this period
         * @param periodHours the scheduling period in hours
         * @return the according schedule representing the planned execution
         */
        public static Schedule atFixedRateFromNextFixedTime(int periodStartHour, int periodHours) {
            // NOTE This scheduler could be improved, for example supporting arbitrary periods (not
            // just
            // hour-based). Also, it probably does not correctly handle all date/time-quirks, for
            // example if a schedule would hit a time that does not exist for a specific date due to
            // DST
            // or similar issues. Those are minor though and can be ignored for now.
            if (periodStartHour < 0 || periodStartHour >= HOURS_OF_DAY) {
                throw new IllegalArgumentException(
                        "Schedule period start hour must be a valid hour of a day (0-23)");
            }
            if (periodHours <= 0 || periodHours > HOURS_OF_DAY) {
                throw new IllegalArgumentException(
                        "Schedule period must not be zero and must fit into a single day (0-24)");
            }

            // Compute fixed schedule hours
            List<Integer> fixedScheduleHours = new ArrayList<>();

            for (int hour = periodStartHour; hour < HOURS_OF_DAY; hour += periodHours) {
                fixedScheduleHours.add(hour);
            }

            Instant now = Instant.now();
            Instant nextFixedTime =
                    computeClosestNextScheduleDate(now, fixedScheduleHours, periodHours);
            return new Schedule(ScheduleMode.FIXED_RATE,
                    ChronoUnit.SECONDS.between(now, nextFixedTime),
                    TimeUnit.HOURS.toSeconds(periodHours), TimeUnit.SECONDS);
        }

        private static Instant computeClosestNextScheduleDate(Instant instant,
                List<Integer> scheduleHours, int periodHours) {
            OffsetDateTime offsetDateTime = instant.atOffset(ZoneOffset.UTC);
            BiFunction<OffsetDateTime, Integer, Instant> dateAtTime =
                    (date, hour) -> date.with(LocalTime.of(hour, 0)).toInstant();

            // The instant is either before the given hours, in between, or after.
            // For latter, we roll the schedule over once to the next day
            List<Instant> scheduleDates = scheduleHours.stream()
                .map(hour -> dateAtTime.apply(offsetDateTime, hour))
                .collect(Collectors.toCollection(ArrayList::new));
            int rolloverHour = (scheduleHours.getLast() + periodHours) % HOURS_OF_DAY;
            scheduleDates.add(dateAtTime.apply(offsetDateTime.plusDays(1), rolloverHour));

            return scheduleDates.stream()
                .filter(instant::isBefore)
                .min(Comparator.comparing(scheduleDate -> Duration.between(instant, scheduleDate)))
                .orElseThrow();
        }
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
