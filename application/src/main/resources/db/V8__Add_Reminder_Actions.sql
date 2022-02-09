CREATE TABLE reminder_actions
(
    id                INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    issued_at         TIMESTAMP NOT NULL,
    author_id         BIGINT    NOT NULL,
    expires_at        TIMESTAMP NOT NULL,
    expired           INT NOT NULL,
    optional_message VARCHAR(100),
    optional_mention VARCHAR(25)
)