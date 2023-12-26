CREATE TABLE component_ids
(
    uuid         TEXT      NOT NULL UNIQUE PRIMARY KEY,
    component_id TEXT      NOT NULL,
    last_used    TIMESTAMP NOT NULL,
    lifespan     TEXT      NOT NULL
)
