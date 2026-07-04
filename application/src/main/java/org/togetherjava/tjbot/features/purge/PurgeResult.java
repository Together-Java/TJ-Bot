package org.togetherjava.tjbot.features.purge;

/**
 * Callback invoked by {@link PurgeCommand} once a purge operation finishes, carrying the total
 * number of messages that were submitted for deletion.
 */
@FunctionalInterface
public interface PurgeResult {
    /**
     * Called when the purge completes.
     *
     * @param totalDeleted the cumulative number of messages submitted for deletion across all
     *        batches of the purge
     */
    void run(int totalDeleted);
}
