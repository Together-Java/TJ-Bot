package org.togetherjava.tjbot.logwatcher.watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreamWatcher {
    private static final int EXPECTED_CONCURRENT_LOG_WATCHERS = 3;
    private static final Map<UUID, Runnable> consumerMap =
            new ConcurrentHashMap<>(EXPECTED_CONCURRENT_LOG_WATCHERS);
    private static final Logger logger = LoggerFactory.getLogger(StreamWatcher.class);

    private StreamWatcher() {}

    /**
     * Signals an intent to be notified on new Entries
     *
     * @param uuid Unique Object to remove the Runnable later
     * @param onNewEvent Run on new Event
     */
    public static void addSubscription(final UUID uuid, final Runnable onNewEvent) {
        consumerMap.put(uuid, onNewEvent);
    }

    /**
     * Removes the Subscription to save resources
     *
     * @param uuid Unique Object used to register the Subscription
     */
    public static void removeSubscription(final UUID uuid) {
        consumerMap.remove(uuid);
    }

    /**
     * Notify all Subscribers with their Runnable
     */
    public static void notifyOfEvent() {
        consumerMap.values().forEach(StreamWatcher::notifySubscriber);
    }

    /**
     * Runs the runnable and logs any errors that might occur
     *
     * @param run The Runnable of one of the consumers
     */
    private static void notifySubscriber(Runnable run) {
        try {
            run.run();
        } catch (final Exception e) {
            logger.error("Runnable threw Exception.", e);
        }
    }
}
