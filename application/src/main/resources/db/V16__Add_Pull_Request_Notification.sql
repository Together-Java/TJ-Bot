CREATE TABLE pr_notifications
(
    id INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    channel_id BIGINT NOT NULL,
    repository_owner TEXT NOT NULL,
    repository_name TEXT NOT NULL
)
