# Overview

This tutorial shows how to add custom commands to the bot.

## Prerequisites
* [[Setup project locally]]
  * you can run the bot locally from your IDE and connect it to a server

## What you will learn
* the basic architecture of the code
* how the command system works
* how to add your own custom command
* basics of JDA, used to communicate with Discord
* basics of jOOQ, used to interact with databases
* basics of SLF4J, used for logging

# Tutorial

## Code architecture

Before we get started, we have to familiarize with the general code structure.

![High level flow](https://i.imgur.com/M8381Zm.png)

The entry point of the bot is `Application`, which will first create instances of:
* `Config`, which provides several properties read from a configuration file
* `Database`, a general purpose database used by the bot and its commands
* `JDA`, the main instance of the framework used to communicate with Discord

The `Config` is available to everyone from everywhere, it is a global singleton. You can just write `Config.getInstance()` and then use its properties. The `Database` is available to all commands, also for your custom command. You can read and write any data to it. From within a command, the `JDA` instance will also be available at any time. Almost all JDA objects, such as the events, provide a `getJDA()` method.

Next, the application will setup the command system.

## Command system

The command system is based around the class `CommandSystem`, which is registered as command handler to `JDA`. It receives all command events from JDA and forwards them to the corresponding registered commands.

Custom commands are added to the `Commands` class, where `CommandSystem` will fetch them by using its `createSlashCommands` method, also providing the database instance. This method could for example look like:
```java
public static Collection<SlashCommand> createSlashCommands(Database database) {
    return List.of(new PingCommand(), new DatabaseCommand(database));
}
```
As an example, when someone uses the `/ping` command, the event will be send to `CommandSystem` by JDA, which will then forward it to the `PingCommand` class.

![command system](https://i.imgur.com/EJNanvE.png)

Commands have to implement the `SlashCommand` interface. Besides metadata (e.g. a name) and the command setup provided by `getData()`, it mostly demands implementation of the event action handlers:
* `onSlashCommand`
* `onButtonClick`
* `onSelectionMenu`

It is also possible to extend `SlashCommandAdapter` which already implemented all methods besides `onSlashCommand`.

Therefore, a minimal example command, could look like:
```java
public final class PingCommand extends SlashCommandAdapter {
    public PingCommand() {
        super("ping", "Bot responds with 'Pong!'", SlashCommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.reply("Pong!").queue();
    }
}
```

## Add your own commands

In the following, we will add two custom commands to the application:
* `/days <from> <to>`
  * computes the difference in days between the given dates
  * e.g. `/days 26.09.2021 03.10.2021` will respond with `8 days`
* `/question ask <question_name> <question>`, `/question get <question_name>`
  * asks a question and users can click a `Yes` or `No` button
  * the choice will be saved in the database from which it can be retrieved using the `get` subcommand
  * e.g. `/question ask "noodles" "Do you like noodles?"` and `/question get "noodles"`

### `days` command

#### Create class

To get started, we have to create a new class, such as `DaysCommand`. A good place for it would be in the `org.togetherjava.tjbot.commands` package. Maybe in a new subpackage or just in the existing `org.togetherjava.tjbot.commands.base` package.

The class has to implement `SlashCommand`, or alternatively just extend `SlashCommandAdapter` which gets most of the work done already. For latter, we have to add a constructor that provides a `name`, a `description` and the command `visibility`. Also, we have to implement the `onSlashCommand` method, which will be called by the system when `/days` was triggered by an user. To get started, we will just respond with `Hello World`. Our first version of this class looks like:
```java
package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

public final class DaysCommand extends SlashCommandAdapter {

    public DaysCommand() {
        super("days", "Computes the difference in days between given dates", SlashCommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.reply("Hello World!").queue();
    }
}
```
#### Register command
Next up, we have to register the command in the command system. Therefore, we open the `Commands` class (in package `org.togetherjava.tjbot.commands`) and simply append an instance of our new command to the `createSlashCommands` method. For example:
```java
public static @NotNull Collection<SlashCommand> createSlashCommands(@NotNull Database database) {
    return List.of(new PingCommand(), new DatabaseCommand(database), new DaysCommand());
}
```
#### Try it out
The command is now ready and can already be used. After starting up the bot, we have to use `/reload` to tell Discord that we changed the slash-commands. Now, we can use `/days` and it will respond with `"Hello World!"`.

![days command hello world](https://i.imgur.com/BVaIfKw.png)

#### Add options
The next step is to add the two options to our command, i.e. being able to write something like `/days 26.09.2021 03.10.2021`. The options are both supposed to be **required**

This has to be configured during the setup of the command, via the `CommandData` returned by `getData()`. We should do this in the constructor of our command. Like so:
```java
public DaysCommand() {
    super("days", "Computes the difference in days between given dates",
            SlashCommandVisibility.GUILD);

    getData().addOption(OptionType.STRING, "from",
                    "the start date, in the format 'dd.MM.yyyy'", true)
            .addOption(OptionType.STRING, "to",
                    "the end date, in the format 'dd.MM.yyyy'", true);
}
```
For starters, let us try to respond back with both entered values instead of just writing `"Hello World!"`. Therefore, in `onSlashCommand`, we retrieve the entered values using `event.getOption(...)`, like so:
```java
@Override
public void onSlashCommand(@NotNull SlashCommandEvent event) {
    String from = event.getOption("from").getAsString();
    String to = event.getOption("to").getAsString();

    event.reply(from + ", " + to).queue();
}
```

If we restart the bot, pop `/reload` again (since we added options to the command), we should now be able to enter two values and the bot will respond back with them:

![days command options dialog](https://i.imgur.com/5yt5EZl.png)
![days command options response](https://i.imgur.com/JNYTVak.png)

#### Date validation

The bot still allows us to enter any string we want. While it is not possible to restrict the input directly in the dialog box, we can easily refuse any invalid input and respond back with an error message instead. We can also use `setEphemeral(true)` on the `reply`, to make the error message only appear to the user who triggered the command.

All in all, the code for the method now looks like:
```java
String from = event.getOption("from").getAsString();
String to = event.getOption("to").getAsString();

var formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
try {
    LocalDate fromDate = LocalDate.parse(from, formatter);
    LocalDate toDate = LocalDate.parse(to, formatter);

    event.reply(from + ", " + to).queue();
} catch (DateTimeParseException e) {
    event.reply("The dates must be in the format 'dd.MM.yyyy', try again.")
        .setEphemeral(true)
        .queue();
}
```
For trying it out, we do not have to use `/reload` again, since we only changed our logic but not the command structure itself.

![days command invalid input](https://i.imgur.com/nB7siQV.png)

#### Compute days

Now that we have two valid dates, we only have to compute the difference in days and respond back with the result. Luckily, the `java.time` API got us covered, we can simply use `ChronoUnit.DAYS.between(fromDate, toDate)`:
```java
long days = ChronoUnit.DAYS.between(fromDate, toDate);
event.reply(days + " days").queue();
```

![days command days difference](https://i.imgur.com/x2E4c4s.png)

#### Full code
After some cleanup and minor code improvements, the full code for `DaysCommand` is:
```java
package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * This creates a command called {@code /days}, which can calculate the difference between two given
 * dates in days.
 * <p>
 * For example:
 * 
 * <pre>
 * {@code
 * /days 26.09.2021 03.10.2021
 * // TJ-Bot: The difference between 26.09.2021 and 03.10.2021 are 7 days
 * }
 * </pre>
 */
public final class DaysCommand extends SlashCommandAdapter {
    private static final String FROM_OPTION = "from";
    private static final String TO_OPTION = "to";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Creates an instance of the command.
     */
    public DaysCommand() {
        super("days", "Computes the difference in days between given dates",
                SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.STRING, FROM_OPTION, "the start date, in the format 'dd.MM.yyyy'",
                    true)
            .addOption(OptionType.STRING, TO_OPTION, "the end date, in the format 'dd.MM.yyyy'",
                    true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String from = Objects.requireNonNull(event.getOption(FROM_OPTION)).getAsString();
        String to = Objects.requireNonNull(event.getOption(TO_OPTION)).getAsString();

        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(from, FORMATTER);
            toDate = LocalDate.parse(to, FORMATTER);
        } catch (DateTimeParseException e) {
            event.reply("The dates must be in the format 'dd.MM.yyyy', try again.")
                .setEphemeral(true)
                .queue();
            return;
        }

        long days = ChronoUnit.DAYS.between(fromDate, toDate);
        event.reply("The difference between %s and %s are %d days".formatted(from, to, days))
            .queue();
    }
}
```

### `question` command

TODO ...