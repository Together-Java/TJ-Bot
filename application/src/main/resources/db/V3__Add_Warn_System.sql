CREATE TABLE warns
(
    userid        BIGINT  NOT NULL PRIMARY KEY,
    guild_id       BIGINT  NOT NULL,
    warning_amount INTEGER NOT NULL
)