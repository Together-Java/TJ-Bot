CREATE TABLE cake_days
(
    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    joined_month_day 	TEXT NOT NULL,
    joined_year         INT NOT NULL,
    guild_id            BIGINT NOT NULL,
    user_id             BIGINT NOT NULL
);

CREATE INDEX cake_day_idx ON cake_days(joined_month_day);