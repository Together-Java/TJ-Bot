# Overview

TJ-Bot is a classic Discord bot with a slim but modern set of dependencies.

## Core

The project stays up to date with the latest Java version.

We use [JDA](https://github.com/DV8FromTheWorld/JDA) to communicate with the Discord API and [Gradle](https://gradle.org/) to manage the project and its dependencies.

## Database

The bot uses a single [SQLite](https://www.sqlite.org/index.html) database, which is generated automatically by [Flyway](https://flywaydb.org/) based on the scripts found in
```
TJ-Bot/application/src/main/resources/db/
```
Interaction with the database is then done using [jOOQ](https://www.jooq.org/).

## Logging

We rely on [SLF4J](http://www.slf4j.org/) for logging, backed by [Log4j 2](https://logging.apache.org/log4j/2.x/).

The configuration can be found at
```
TJ-Bot/application/src/main/resources/log4j2.xml
```

## Testing

For testing the project, we use [JUnit 5](https://junit.org/junit5/docs/current/user-guide/).

## Code Quality

The quality of the code is ensured by [Spotless](https://github.com/diffplug/spotless), using a strict style based on the commonly used [Google Java Style](https://google.github.io/styleguide/javaguide.html). The exact style definition can be found at:
```
TJ-Bot/meta/formatting/google-style-eclipse.xml
```

Additionally, we use static code analyse by [SonarCloud](https://sonarcloud.io/dashboard?id=Together-Java_TJ-Bot).

Further, the code is checked automatically by [CodeQL](https://codeql.github.com/docs/) and dependencies are kept up to date with the aid of [Dependabot](https://dependabot.com/)

## Deployment

In order for the bot to actually go live, it is deployed as [Docker](https://www.docker.com/) image, build by [jib](https://github.com/GoogleContainerTools/jib), to a VPS provided by [Hetzner](https://www.hetzner.com/) (see [[How to access the VPS]] for details).