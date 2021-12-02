CREATE TABLE warn_system
(
    case_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        BIGINT NOT NULL,
    guild_id       BIGINT NOT NULL,
    warn_reason    TEXT DEFAULT 'This user has been warned for breaking the rules.',
    action_type    TEXT DEFAULT 'warned',
    timestamp      TIMESTAMP NOT NULL,
    warning_amount INTEGER DEFAULT 0
)