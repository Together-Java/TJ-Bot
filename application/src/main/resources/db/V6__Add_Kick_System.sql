CREATE TABLE kick_system
(
    case_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        BIGINT NOT NULL,
    guild_id       BIGINT NOT NULL,
    reason    TEXT  DEFAULT 'This user has been kicked for breaking the rules.',
    action_type    TEXT DEFAULT 'warned',
    timestamp      TIMESTAMP NOT NULL
)