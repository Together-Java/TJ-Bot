CREATE TABLE mod_audit_log_guild_process
(
    guild_id                       BIGINT    NOT NULL PRIMARY KEY,
    last_processed_audit_log_entry TIMESTAMP NOT NULL
)
