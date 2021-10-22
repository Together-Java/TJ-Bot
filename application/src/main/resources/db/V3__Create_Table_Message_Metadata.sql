CREATE TABLE message_metadata(
    message_id       BIGINT NOT NULL PRIMARY KEY,
    guild_id         BIGINT NOT NULL,
    channel_id       BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,
    create_timestamp BIGINT NOT NULL
)
