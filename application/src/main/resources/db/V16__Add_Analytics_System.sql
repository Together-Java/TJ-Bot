CREATE TABLE command_usage
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id      INTEGER NOT NULL,
    command_name  TEXT    NOT NULL,
    user_id       INTEGER NOT NULL,
    executed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success       BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT
);

CREATE INDEX idx_command_usage_guild ON command_usage(guild_id);
CREATE INDEX idx_command_usage_command_name ON command_usage(command_name);
