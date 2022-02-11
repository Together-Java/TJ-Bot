# Overview

In order to reset of edit the databases used by the bot, one has to login to the VPS and navigate to the corresponding directory. Only members of the [Moderator](https://github.com/orgs/Together-Java/teams/moderators)-Team can do the following steps.

See [[Access the VPS]] for details of the login process.

# Guide

1. `ssh togetherjava` to login to the VPS
2. Consider temporarilly shutting down the bot during the database edits (see [[Shutdown or restart the bot]])
3. Either `cd /var/lib/docker/volumes/tj-bot-master-database/_data` or `cd /var/lib/docker/volumes/tj-bot-develop-database/_data` to go to the directory of the corresponding database
4. Edit the database manually, it is a [SQLite 3](https://www.sqlite.org/index.html) database.

## Working with the database

To ease inspecting and editing the database, the `sqlite3` CLI is installed on the VPS. Here are some simple example queries:
* List all available tables:
```sql
.tables
```
* Show the structure of the table
```sql
.schema moderation_actions
```
* Show all against against a user
```sql
SELECT * FROM moderation_actions WHERE author_id = 123456789
```

![example](https://i.imgur.com/zmJtYrD.png)