CREATE TABLE sticky_message
(
    channel_id BIGINT NOT NULL PRIMARY KEY,
    text       TEXT   NOT NULL,
    message_id BIGINT NOT NULL
)