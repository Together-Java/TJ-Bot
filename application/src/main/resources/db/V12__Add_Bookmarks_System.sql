CREATE TABLE bookmarks
(
    author_id  BIGINT    NOT NULL,
    channel_id BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL,
    note       TEXT,
    delete_at  TIMESTAMP,

    PRIMARY KEY (author_id, channel_id)
)
