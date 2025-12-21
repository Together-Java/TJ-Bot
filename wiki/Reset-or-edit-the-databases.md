# Overview

In order to reset of edit the databases used by the bot, one has to login to the VPS and navigate to the corresponding directory. Only members of the [Moderator](https://github.com/orgs/Together-Java/teams/moderators)-Team can do the following steps.

See [[Access the VPS]] for details of the login process.

# Guide

1. `ssh togetherjava` to login to the VPS
2. Consider temporarilly shutting down the bot during the database edits (see [[Shutdown or restart the bot]])
3. Either `cd /var/lib/docker/volumes/tj-bot-master-database/_data` or `cd /var/lib/docker/volumes/tj-bot-develop-database/_data` to go to the directory of the corresponding database
4. Edit the database manually, it is a [SQLite 3](https://www.sqlite.org/index.html) database.

## Working with the database

To ease inspecting and editing the database, the `sqlite3` CLI is installed on the VPS.

Please make sure to either shut down the bot in the meantime or working on a copy of the database instead, to **avoid locking the actual database**:
```bash
cp database.db database_copy.db
```

Here are some simple example queries:
* Connect to the database
```bash
sqlite3 database_copy.db
```
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
* Exist the database
```sql
.exit
```

![example](https://i.imgur.com/zmJtYrD.png)