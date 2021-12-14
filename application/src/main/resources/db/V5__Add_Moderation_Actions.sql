CREATE TABLE moderation_actions
(
    case_id           INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    issued_at         TIMESTAMP NOT NULL,
    guild_id          BIGINT    NOT NULL,
    author_id         BIGINT    NOT NULL,
    target_id         BIGINT    NOT NULL,
    action_type       TEXT      NOT NULL,
    action_expires_at TIMESTAMP,
    reason            TEXT      NOT NULL
)