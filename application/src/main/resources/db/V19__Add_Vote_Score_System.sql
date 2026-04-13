CREATE TABLE vote_score
(
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vote INTEGER NOT NULL,
    PRIMARY KEY (message_id, user_id)
)