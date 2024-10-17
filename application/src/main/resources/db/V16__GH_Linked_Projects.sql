CREATE TABLE gh_linked_projects
(
    channelId       TEXT NOT NULL PRIMARY KEY,
    repositoryOwner TEXT NOT NULL,
    repositoryName  TEXT NOT NULL
)
