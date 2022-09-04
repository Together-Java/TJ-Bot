CREATE TABLE pending_reminders
(
    id         INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    guild_id   BIGINT    NOT NULL,
    channel_id BIGINT    NOT NULL,
    author_id  BIGINT    NOT NULL,
    remind_at  TIMESTAMP NOT NULL,
    content    TEXT      NOT NULL
)