CREATE TABLE storage
(
    key   TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
)
CREATE TABLE warn
(
    user_id  INTEGER  NOT NULL,
    guild_id INTEGER  NOT NULL,
    warns    INTEGER  NOT NULL
)
