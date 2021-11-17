CREATE TABLE warn_system
(
    /**
     * This was taken from https://github.com/YusufsDiscordbot/Yusuf-s-Moderation-Bot and changed.
     */
    userid         BIGINT NOT NULL UNIQUE PRIMARY KEY,
    guild_id       BIGINT NOT NULL,
    warn_reason    TEXT DEFAULT 'This user has been warned for breaking the rules.',
    is_warned      BIT DEFAULT FALSE,
    warning_amount INTEGER DEFAULT 0
)