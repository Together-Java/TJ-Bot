package org.togetherjava.tjbot.commands.moderation.scam;

import net.dv8tion.jda.api.JDA;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.Routine;

import java.time.Instant;
import java.time.Period;
import java.util.concurrent.TimeUnit;

/**
 * Cleanup routine to get rid of old scam history entries in the {@link ScamHistoryStore}.
 */
public final class ScamHistoryPurgeRoutine implements Routine {
    private final ScamHistoryStore scamHistoryStore;
    private static final Period DELETE_SCAM_RECORDS_AFTER = Period.ofWeeks(2);

    /**
     * Creates a new instance.
     * 
     * @param scamHistoryStore containing the scam history to purge
     */
    public ScamHistoryPurgeRoutine(@NotNull ScamHistoryStore scamHistoryStore) {
        this.scamHistoryStore = scamHistoryStore;
    }

    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.DAYS);
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        scamHistoryStore.deleteHistoryOlderThan(Instant.now().minus(DELETE_SCAM_RECORDS_AFTER));
    }
}
