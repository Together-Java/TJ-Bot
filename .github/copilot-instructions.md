[//]: # (This file is not a README, it is for GitHub Copilot not for contributors.)

# Copilot Instructions

## Overview
This project is a [Discord](https://discord.com/) Bot developed in Java, utilizing [JDA (Java Discord API)](https://jda.wiki/introduction/jda/) library for diverse interactions on Together Java discord server.

## Tech Stack

The project uses a variety of technologies and libraries to achieve its functionality. Below is a comprehensive list of the main technologies used :

**Languages** 
- Java: the primary language for the project.
- SQL: for database creation and management.
- Groovy: for build-tool's configurations.

**Build & Dependency Management**
- Gradle v8: the build tool used for managing dependencies and building the project.
- Shadow plugin: for creating a fat JAR with all dependencies included.
- Jib plugin: for building Docker images directly from Gradle.

**Framework :** JDA (Java Discord API): the main framework for interacting with Discord's API.

**Database**
- SQLite: for local database storage.
- jOOQ (ORM): for database interactions and schema generation.

**Logging**
- Log4j v2: the logging framework used for logging messages.
- log4j-slf4j2-impl: for bridging SLF4J to Log4j v2.

**JSON/XML/CSV Processing**
- Jackson (core, dataformat-csv, dataformat-xml, datatype-jsr310, sealed-classes)

**Utilities**
- jsr305: for annotations from JSR-305.
- JetBrains Annotations: for annotations from JetBrains.
- urlbuilder: for building URLs.
- jsoup: for parsing HTML and extracting data.
- jlatexmath (+ font modules): for rendering LaTeX math expressions.
- ascii-table: for rendering ASCII tables.
- url-detector
- caffeine (caching)
- github-api (kohsuke)
- commons-text
- rssreader
- com.theokanning.openai-gpt3-java (api, service)

**Testing**
- JUnit Jupiter
- Mockito

**Code Quality**
- Spotless
- SonarLint

**Containerization**
- Docker Compose
- Jib (for container image building)

**Version Control & CI/CD**
- Git v2
- GitHub Actions (for CI/CD, under `.github/workflows`)
- Dependabot (for dependency updates, under `.github/dependabot.yml`)
- Docker

**IDE/Editor Support**
- IntelliJ IDEA
- Eclipse
- GitHub Codespaces

## Repository Structure

A **multi-module** Gradle project composed of 5 modules :
```txt
TJ-Bot
├── application (Application module)
├── buildSrc (Custom Gradle plugin)
├── database
├── formatter
├── utils
```

The repository has other directories that are not modules :

- `.devcontainer` and `.vscode` directories are only about giving support to coding on [GitHub's Codespaces](https://github.com/features/codespaces). 
The project is not intended necessarily to be run in a container or a specific IDE_.
- `.github` directory contains configuration files for GitHub Actions, Dependabot, and other GitHub features.
- `.gradle` directory contains the Gradle wrapper files, allowing the project to be built with a specific version of Gradle without requiring users to install it manually, ignore it.
- `meta` directory currently contains 3 [draw.io](https://www.drawio.com/) diagram files about the project, and a `google-style-eclipse.xml` file used in Spotless configuration.
- `scripts` directory currently contains only one file which is a git pre-commit hook that runs Spotless to format the code before committing.

### Module 1: Application 

This is the main module. It contains the entry point, core logic, feature implementations, configuration management, and logging utilities for the bot.

Most packages are equipped with a `package-info.java` file, which contains a brief description and 2 annotations
- `@MethodsReturnNonnullByDefault`: defined in the `/utils` module, indicating that all methods in the package return non-null values by default.
- `@ParametersAreNonnullByDefault`: from `javax.annotation` package, indicating that all parameters in the package are non-null by default.

NOTE: _We recommend to all contributors to only focus on this module. The other ones are handling some aspects behind the scenes, and are not meant to be modified by contributors._

#### 1. `config`: 

Purpose: Handles all configuration aspects of the bot, including loading, validating, and providing access to configuration values.

Contents:
- Classes for parsing and representing configuration files (e.g., `config.json.template`).
- Utilities for environment variable overrides and runtime configuration changes.
- Central access point for configuration values used throughout the application.

#### 2. `features`

Purpose: Implements the bot’s features, including commands, event listeners, and integrations.

Contents:
- Command classes extending `SlashCommandAdapter` for Discord slash commands.
- Event listeners for handling Discord events (e.g., user asked a question, question answered).
- Feature registration and lifecycle management (e.g., Features.createFeatures).
- Sub-packages for organizing features by domain (e.g., basic, chatgpt (openai), code, projects, and others).

#### 3. `logging`

Purpose: Provides logging configuration and utilities for the application.

Contents:
- Log4j2 configuration files (e.g., `resources/log4j2.xml`).
- Custom logging utilities and wrappers.
- Integration with `SLF4J` for consistent logging across dependencies.

### Module 2: BuildSrc

This module doesn't contain any Java files, it contains a build process to the SQLite database using Flyway and JOOQ.

It contains exactly one groovy file database-settings.gradle, which is a custom Gradle plugin that handles the database schema generation and migration.

### Module 3: Database

This module is about creating the connection to the SQLite database and providing a way to interact with it using JOOQ.

### Module 4: Formatter

This module is about formatting code snippets in Discord messages, using the JDA library.

It's a sort of support for the `features.code` package in the `application` module.

> formatter... is a really complex project. it was created as its own module. probably also bc it was originally intended to be run outside the bots context - Zabuzard

### Module 5: Utils

This module contains utility classes and methods that are used across the application. It includes exactly one class `MethodsReturnNonnullByDefault` which is an annotation used in most packages in the `application` module.

## Complex Aspects

This section is about some important aspects in the `application` module. Some of these aspects are complex and may require additional attention when they get modified :

- `BotCore` a class/component defined under a subpackage `features.system`.
- `ComponentIdStore` under a subpackage `features.componentids`.
- `ComponentIdGenerator` under a subpackage `features.componentids`.
- `ComponentIdInteractor` under a subpackage `features.componentids`.
- `ComponentIdParser` under a subpackage `features.componentids`.
- `Lifespan` under a subpackage `features.componentids`.
- The corresponding database table and how it hooks into the actions, i.e. `UserInteractor#acceptComponmentIdGenerator` and the like.
- the unit testing setup around discord mocking: the `JdaTester` class and the files next to it (.e.g the `jda.payloads` package)

NOTE: _These aspects are not meant to be modified by contributors, but they are important to understand how the bot works._

## Coding Style

This section outlines the coding style and conventions to follow when contributing to the project. Adhering to these guidelines ensures consistency and readability across the codebase.

Know that the organization uses [Sonar](https://sonarcloud.io/project/overview?id=Together-Java_TJ-Bot) and Spotless to enforce coding style and formatting rules. 

Try to use the [Together-Java Sonar's Quality Profile](https://sonarcloud.io/organizations/togetherjava/quality_profiles) when you write code or help with completions.

In addition, the following conventions should be followed:
- Use meaningful variable and method names.
- Use `@Override` annotation for overridden methods.
- Use `final` for fields and classes whenever is better.
- Enforce using `this` keyword to refer to the current instance of a class.
- No magic numbers or strings; use constants instead.
- No unnecessary comments; code should be self-explanatory.
- Use Javadoc for public classes and methods.
- No wildcard imports; import only the necessary classes.
- Adhere to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

## Commands
1. Create a new class extending `SlashCommandAdapter`.
2. If the command requires dependencies (e.g., `Database`), accept them in the constructor.
3. Override methods such as `onSlashCommand`, `onButtonClick`, etc., as needed.
4. Add the command to the list in `Features.createFeatures`, passing any required dependencies.

Example:
```java
public class FooCommand extends SlashCommandAdapter {

    private static final String COMMAND_NAME = "foo";

    private final Database database;
    private final Config config;
    
    public FooCommand(Database database, Config config) {
        super(COMMAND_NAME, "Foo command using database and config", CommandVisibility.GUILD);
        this.database = database;
        this.config = config;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        event.reply("Foo !").queue();
    }
}
```

Commands are registered as features in `org.togetherjava.tjbot.features.Features.createFeatures.java`:
```java
public static Collection<Feature> createFeatures(JDA jda, Database database, Config config) {
    // ... some features

    features.add(new FooCommand(database, config));
}
```