CREATE TABLE scam_history
(
    id           INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    sent_at      TIMESTAMP NOT NULL,
    guild_id     BIGINT    NOT NULL,
    channel_id   BIGINT    NOT NULL,
    message_id   BIGINT    NOT NULL,
    author_id    BIGINT    NOT NULL,
    content_hash TEXT      NOT NULL,
    is_deleted   BOOLEAN   NOT NULL
)