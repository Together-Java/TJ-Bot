[//]: # (This file is not a README, it is for GitHub Copilot not for contributors.)

# Copilot Instructions

## Overview
This project is a [Discord](https://discord.com/) Bot developed in Java, utilizing [JDA (Java Discord API)](https://jda.wiki/introduction/jda/) library for diverse interactions on Together Java server. The project is built with Gradle.

## Project Structure

## Complex Aspects

## Security

## Coding Style
- Use meaningful variable and method names.
- Use `@Override` annotation for overridden methods.
- Use `final` for constants and method parameters that should not be modified.
- Use `this` keyword to refer to the current instance of a class.
- No magic numbers or strings; use constants instead.
- No unnecessary comments; code should be self-explanatory.
- Use Javadoc for public classes and methods.
- No wildcard imports; import only the necessary classes.
- Adhere to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
- Use Spotless for code formatting. Run `gradlew spotlessApply` to format code.

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

## Database

## Error Handling

## Logging

## Testing

## Documentation