CREATE TABLE metric_events
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event       TEXT      NOT NULL,
    happened_at TIMESTAMP NOT NULL
);
