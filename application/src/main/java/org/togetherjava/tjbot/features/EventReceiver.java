package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.hooks.EventListener;

/**
 * Receives all incoming Discord events, unfiltered. A list of all available event types can be
 * found in {@link net.dv8tion.jda.api.hooks.ListenerAdapter}.
 *
 * If possible, prefer one of the more concrete features instead, such as {@link SlashCommand} or
 * {@link MessageReceiver}. Take care to not accidentally implement both, this and one of the other
 * {@link Feature}s, as this might result in events being received multiple times.
 * <p>
 * All event receivers have to implement this interface. A new receiver can then be registered by
 * adding it to {@link Features}.
 * <p>
 * <p>
 * After registration, the system will notify a receiver for any incoming Discord event.
 */
@FunctionalInterface
public interface EventReceiver extends EventListener, Feature {
    // Basically a renaming of JDAs EventListener, plus our Feature marker interface
}
