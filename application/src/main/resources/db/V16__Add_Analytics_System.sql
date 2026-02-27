CREATE TABLE command_usage
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id    INTEGER NOT NULL,
    command_name  TEXT    NOT NULL,
    user_id       INTEGER NOT NULL,
    executed_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success       BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT
);

CREATE INDEX idx_command_usage_channel ON command_usage(channel_id);
CREATE INDEX idx_command_usage_command_name ON command_usage(command_name);
