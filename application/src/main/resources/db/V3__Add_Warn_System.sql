CREATE TABLE warns
(
    userid         BIGINT NOT NULL PRIMARY KEY,
    guild_id       BIGINT NOT NULL,
    warn_reason   TEXT NOT NULL,
    warning_amount INTEGER NOT NULL
)