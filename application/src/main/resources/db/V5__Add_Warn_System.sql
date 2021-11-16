CREATE TABLE warn_system
(
    /**
     This was taken from Yusuf's Moderation bot. I the owner allow this.
    */
    userid         BIGINT NOT NULL PRIMARY KEY,
    guild_id       BIGINT NOT NULL,
    warn_reason    TEXT DEFAULT 'This user has been warned for breaking one of the rules.',
    warning_amount INTEGER DEFAULT 0
)