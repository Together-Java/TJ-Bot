CREATE TABLE kick_system
(
    /**
     This was taken from Yusuf's Moderation bot. I the owner allow this.
    */
    userid         BIGINT NOT NULL PRIMARY KEY,
    guild_id       BIGINT NOT NULL,
    author_id      BIGINT NOT NULL,
    kick_reason    TEXT  DEFAULT 'This user has been kicked for breaking one of the rules.'
)