package org.togetherjava.tjbot.features.componentids;

/**
 * The lifespan of a component ID. Controls when it will be targeted for eviction and expire.
 */
public enum Lifespan {
    /**
     * Component IDs with permanent lifespan are never deleted, hence will never expire. Use this
     * with care and only with events that actually have to be usable forever.
     */
    PERMANENT,
    /**
     * Component IDs with a regular lifespan are deleted after some time when needed. Once this
     * happens, their associated event expires and can not be used anymore. This should be the
     * default setting for most component IDs.
     */
    REGULAR
}
