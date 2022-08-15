CREATE TABLE add_help_channel
(
    channel_id BIGINT    NOT NULL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL
)