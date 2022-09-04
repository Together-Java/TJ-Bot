CREATE TABLE help_channel_messages
(
    message_id BIGINT    NOT NULL PRIMARY KEY,
    guild_id   BIGINT    NOT NULL,
    channel_id BIGINT    NOT NULL,
    author_id  BIGINT    NOT NULL,
    sent_at    TIMESTAMP NOT NULL
)
