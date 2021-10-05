CREATE TABLE logevents
(
    id             INTEGER PRIMARY KEY autoincrement,
    time           TIMESTAMP NOT NULL,
    thread         TEXT      NOT NULL,
    level          TEXT      NOT NULL,
    loggerName     TEXT      NOT NULL,
    message        TEXT      NOT NULL,
    endOfBatch     boolean   NOT NULL,
    loggerFqcn     TEXT      NOT NULL,
    threadId       integer   NOT NULL,
    threadPriority integer   NOT NULL
);

CREATE TABLE users
(
    discordID BIGINT PRIMARY KEY,
    userName  TEXT NOT NULL
);

CREATE TABLE userRoles
(
    userID BIGINT,
    roleID INTEGER,

    CONSTRAINT fk_discordID
        FOREIGN KEY(userID)
            REFERENCES users(discordID)
                ON DELETE CASCADE,

    PRIMARY KEY(userID, roleID)
);
