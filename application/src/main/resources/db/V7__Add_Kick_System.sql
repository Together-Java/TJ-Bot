CREATE TABLE kick_system
(
    /**
    * This was taken from https://github.com/YusufsDiscordbot/Yusuf-s-Moderation-Bot and changed.
    */
    userid         BIGINT NOT NULL UNIQUE PRIMARY KEY,
    guild_id       BIGINT NOT NULL,
    author_id      BIGINT NOT NULL,
    is_kicked      BIT DEFAULT FALSE,
    kick_reason    TEXT  DEFAULT 'This user has been kicked for breaking the rules.'
)