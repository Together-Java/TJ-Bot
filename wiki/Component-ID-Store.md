# Overview

[[Component IDs]] are, among other things, used to carry information through an event.

It does so by storing the actual payload outside of the component ID used by the buttons, in a database table (and in-memory map), associating it to a generated **UUID**, which is then used as actual component ID for JDA.

# Component ID Store

The `ComponentIdStore` is the central point for this mechanism, which is exposed via the interfaces:
* `ComponentIdGenerator` - to all commands during setup for id generation
* `ComponentIdParser` - to `CommandSystem` for the routing of events

The store is basically a 2-layer `Map<UUID, ComponentId>`:
* first layer: `Cache` (by Caffeine), to speedup lookups, covers probably about 95% of all queries or so
* second layer: a database table `component_ids`

When an user wants to create a component ID, the store will add the payload to both layers and associate it to a generated UUID. When an user wants to parse back the payload from the UUID, the store looks up the in-memory map first, and if not found, also the database.

## Eviction

To prevent the database from just growing indefinitely over the years, the `ComponentIdStore` implements a LRU-mechanism on both, the in-memory map, as well as on the database table.

For latter, it runs a periodic **eviction-routine**, which will locate records that have not been used for a long time and delete them.

Each lookup of an UUID **heats** the record in both, the in-memory map and in the database table. That means, its `last_used` timestamp will be updated, making it not targeted for the next evictions.

Users are able to listen to eviction events, by registering themselves as listener using `ComponentIdStore#addComponentIdRemovedListener`. While not used as of today, this might be interesting in the future, for example to deactivate actions (such as buttons) associated to an expired component ID.

Component IDs can also be associated with a `Lifespan.PERMANENT` to prevent eviction all together. While this should not be used per default, it can be useful for actions that are not used often but should still be kept alive (for example global role-assignment reactions).

Eviction details can be configured, but by default they are set to:
- evict every **15 minutes**
- evict entries that have not been used for longer than **20 days**
- in-memory map has a max size of **1_000**

## Database details

The table is created as such:
```sql
CREATE TABLE component_ids
(
    uuid         TEXT      NOT NULL UNIQUE PRIMARY KEY,
    component_id TEXT      NOT NULL,
    last_used    TIMESTAMP NOT NULL,
    lifespan     TEXT      NOT NULL
)
```
Content looks for example like this (after popping `/reload` three times):
![table records](https://user-images.githubusercontent.com/13614011/137593105-1cb99a80-ee6d-46c0-8a5b-d1666eb553fb.png)