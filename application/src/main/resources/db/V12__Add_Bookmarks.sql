CREATE TABLE bookmarks
(
    creator_id      BIGINT    NOT NULL,
    channel_id      BIGINT    NOT NULL,
    original_title  TEXT      NOT NULL,
    last_renewed_at TIMESTAMP NOT NULL,
    note            TEXT,
    PRIMARY KEY (creator_id, channel_id)
)
