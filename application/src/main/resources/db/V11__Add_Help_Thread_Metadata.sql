CREATE TABLE help_threads
(
    channel_id BIGINT    NOT NULL PRIMARY KEY,
    author_id  BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL
)