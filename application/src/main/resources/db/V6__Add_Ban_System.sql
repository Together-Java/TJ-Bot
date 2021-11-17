CREATE TABLE ban_system
(
    /**
    * This was taken from https://github.com/YusufsDiscordbot/Yusuf-s-Moderation-Bot and changed.
    */
    userid         BIGINT NOT NULL UNIQUE PRIMARY KEY,
    guild_id       BIGINT NOT NULL,
    author_id      BIGINT NOT NULL,
    is_banned      BIT DEFAULT FALSE,
    ban_reason     TEXT DEFAULT 'This user has been banned for breaking the rules.'
)