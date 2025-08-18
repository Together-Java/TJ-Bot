[//]: # (This file is not a README, it is for [GitHub Copilot](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions) to help the AI tool to understand the project and provide better completions and code generations.)

# Copilot Instructions

## Overview
This project is a [Discord](https://discord.com/) Bot developed in Java, utilizing [JDA (Java Discord API)](https://jda.wiki/introduction/jda/) library for diverse interactions on Together Java discord server.

## Tech Stack

The project uses a variety of technologies and libraries to achieve its functionality. Below is a comprehensive list of the main technologies used:

**Languages** 
- Java: the primary language for the project.
- SQL: for database creation and management.
- Groovy: for build-tool's configurations.

**Build & Dependency Management**
- Gradle: Be aware of the Gradle version in use, it is written in `/gradle/wrapper/gradle-wrapper.properties`.
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
- Git
- GitHub Actions (for CI/CD, under `.github/workflows`)
- Dependabot (for dependency updates, under `.github/dependabot.yml`)
- Docker

**IDE/Editor Support**

The project is designed to be compatible with various IDEs.

## Repository Structure

A **multi-module** Gradle project composed of 5 modules:

1. **Application:** This is the main module. It contains the entry point, core logic, feature implementations, configuration management, and logging utilities for the bot.
2. **BuildSrc:** Doesn't contain any Java files, it contains a build process to the SQLite database using Flyway and JOOQ. It contains exactly one [Groovy](https://groovy-lang.org/) file `database-settings.gradle`, which is a custom Gradle plugin that handles the database schema generation and migration.
3. **Database:** about creating the connection to the SQLite database and providing a way to interact with it using JOOQ.
4. **Formatter:** about formatting code snippets in any text messages, using [JDA](https://github.com/discord-jda/JDA?tab=readme-ov-file#jda-java-discord-api). It's built in a way that its robust and works even for code that doesn't compile, snippets and the like
5. **Utils:** contains utility classes and methods that are used across the application. It includes exactly one class `MethodsReturnNonnullByDefault`: an annotation used in most packages as a validation mechanism to ensure that methods return non-null values by default, unless explicitly annotated otherwise.

The repository has other directories that are not modules:

- `.devcontainer` and `.vscode` directories are about giving support to coding on [GitHub's Codespaces](https://github.com/features/codespaces). The `.vscode` can also be used to support developers on [VS Code](https://code.visualstudio.com/) IDE. A local setup does not require running the project from a container like Docker or the use of a specific IDE.
- `.github` directory contains configuration files for GitHub Actions, Dependabot, and other GitHub features.
- `.gradle` directory contains the Gradle wrapper files, allowing the project to be built with a specific version of Gradle without requiring users to install it manually, ignore it.
- `meta` directory contains 3 [draw.io](https://www.drawio.com/) diagram files about the project, and a `google-style-eclipse.xml` file used in Spotless configuration.
- `scripts` directory contains only one file which is a git pre-commit hook that runs Spotless to format the code before committing.
- `wiki` directory contains the project's wiki pages, which are used to document the project and its features. The wiki is an important reference to understanding core logic and coding style.

## Coding Style

This section outlines the coding style and conventions to follow when contributing to the project. Adhering to these guidelines ensures consistency and readability across the codebase.

Know that the organization uses [Sonar](https://sonarcloud.io/project/overview?id=Together-Java_TJ-Bot) and Spotless to enforce coding style and formatting rules. 

Try to use the [Together-Java Sonar's Quality Profile](https://sonarcloud.io/organizations/togetherjava/quality_profiles) when you write code or help with completions.

In addition, the following conventions should be followed:
- Use meaningful class, method and variable names.
- Use `final` for fields and classes as default. Only make them non-final when modification and extension becomes necessary. In which case it is important to document its interface and intended use with Javadoc.
- Enforce using `this` keyword to refer to the current instance of a class.
- It's generally preferred to use well named constants over magic values.
- Prefer writing self-explanatory code, employ clean coding techniques. 
- Introduce helper method when necessary before using a comment instead.
- Use Javadoc for public classes and methods.
- No wildcard imports; import only the necessary classes.
