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
* `/question ask <id> <question>`, `/question get <id>`
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

#### Setup

The next command focuses on how to use sub-commands and a database. We start with the same base setup as before, but this time we need a `Database` argument:
```java
public final class QuestionCommand extends SlashCommandAdapter {
    private final Database database;

    public QuestionCommand(Database database) {
        super("question", "Asks users questions, responses are saved and can be retrieved back",
                SlashCommandVisibility.GUILD);
        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.reply("Hello World!").queue();
    }
}
```
Also, we again have to register our new command by adding it to the list of commands in the `Commands` class, but this time providing the database instance:
```java
public static @NotNull Collection<SlashCommand> createSlashCommands(@NotNull Database database) {
    return List.of(new PingCommand(), new DatabaseCommand(database), new QuestionCommand(database));
}
```

#### Add sub-commands

As first step, we have to add the two sub-commands `ask` and `get` to the command.
* `ask` expects two options, `id` and `question`,
* while `get` only expects one option, `id`.

We can configure both, the sub-commands and their options, again via the `CommandData` object returned by `getData()`, which has to be done during construction of the command:
```java
public QuestionCommand(Database database) {
    super("question", "Asks users questions, responses are saved and can be retrieved back",
            SlashCommandVisibility.GUILD);
    this.database = database;

    getData().addSubcommands(
        new SubcommandData("ask", "Asks the users a question, responses will be saved")
            .addOption(OptionType.STRING, "id", "Unique ID under which the question should be saved", true)
            .addOption(OptionType.STRING, "question", "Question to ask", true),
        new SubcommandData("get", "Gets the response to the given question")
            .addOption(OptionType.STRING, "id", "Unique ID of the question to retrieve", true));
}
```
We can retrieve back the used sub-command using `event.getSubcommandName()`, and the corresponding option values using `event.getOption(...)`. To simplify handling the command, we split them into two helper methods and `switch` on the command name:
```java
@Override
public void onSlashCommand(@NotNull SlashCommandEvent event) {
    switch (Objects.requireNonNull(event.getSubcommandName())) {
        case "ask" -> handleAskCommand(event);
        case "get" -> handleGetCommand(event);
        default -> throw new AssertionError();
    }
}

private void handleAskCommand(@NotNull SlashCommandEvent event) {
    String id = event.getOption("id").getAsString();
    String question = event.getOption("question").getAsString();

    event.reply("Ask command: " + id + ", " + question).queue();
}

private void handleGetCommand(@NotNull SlashCommandEvent event) {
    String id = event.getOption("id").getAsString();

    event.reply("Get command: " + id).queue();
}
```

#### Try it out

At this point, we should try out the code. Do not forget to use `/reload` before though. You should now be able to use `/question ask` with two required options and `/question get` with only one required option. And the bot should respond back correspondly.

![question sub commands](https://i.imgur.com/3MI6ITN.png)

#### Add buttons

Instead of just writing down a question, we also want to give the user the opportunity to respond by clicking one of two buttons. This can be done by using `.addActionRow(...)` on our `reply` and then making use of `Button.of(...)`.

Note that a button needs a so called **component ID**. The rules for this id are quite complex and can be read about in the documentation of `SlashCommand#onSlashCommand`. Fortunately, there is a helper that can generate component IDs easily. Since we extended `SlashCommandAdapter`, it is already directly available as `generateComponentId()` (alternatively, use the helper class `ComponentIds`).

Additionally, we have to remember the question ID during the dialog, since we still need to be able to save the response under the question ID in the database. The button component ID can be used for such a situation, we can just call the generator method with arguments, like `generateComponentId(id)`, and will be able to retrieve them back later on.

The full code for the `handleAskCommand` method is now:
```java
private void handleAskCommand(@NotNull SlashCommandEvent event) {
String id = event.getOption("id").getAsString();
String question = event.getOption("question").getAsString();

event.reply(question)
        .addActionRow(
                Button.of(ButtonStyle.SUCCESS, generateComponentId(id), "Yes"),
                Button.of(ButtonStyle.DANGER, generateComponentId(id), "No"))
        .queue();
}
```
When trying it out, we can now see the question and two buttons to respond:

![question add buttons](https://i.imgur.com/KGr9hl6.png)

However, clicking the buttons still does not trigger anything yet.

#### React to button click

In order to react to a button click, we have to give an implementation for the `onButtonClick(...)` method, which `SlashCommandAdapter` already implemented, but without any action. The method provides us wit hthe `ButtonClickEvent` and also with a `List<String>` of arguments, which are the optional arguments added to the **component id** earlier. In our case, we added the question id, so we can also retrieve it back now by using `args.get(0)`. Also, we can figure out which button was clicked by using `event.getButton().getStyle()`.

A minimal setup could now look like:
```java
@Override
public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
    String id = args.get(0);
    ButtonStyle buttonStyle = Objects.requireNonNull(event.getButton()).getStyle();

    boolean clickedYes = switch (buttonStyle) {
        case DANGER -> false;
        case SUCCESS -> true;
        default -> throw new AssertionError("Unexpected button action clicked: " + buttonStyle);
    };

    event.reply("id: " + id + ", clickedYes: " + clickedYes).queue();
}
```
Clicking the buttons now works:

![question react to buttons](https://i.imgur.com/39SjSI2.png)

#### Disable buttons after click

Right now, the buttons can be clicked as often as wanted and the bot will always be triggered again. To get rid of this, we simply have to disable the buttons after someone clicked.

We can do so by using `event.getMessage().editMessageComponents(...)` and then providing a new list of components, i.e. the previous buttons but with `button.asDisabled()`. We can get hands on the previous buttons by using `event.getMessage().getButtons()`.

Long story short, we can simply add:
```java
event.getMessage()
    .editMessageComponents(ActionRow
        .of(event.getMessage().getButtons().stream().map(Button::asDisabled).toList()))
    .queue();
```
and the buttons will be disabled after someone clicks them:

![question ask disable button](https://i.imgur.com/Wf8NQ7U.png)

#### Setup database

Last but not least for the `ask` command, we have to save the response in the database. Before we can get started with this, we have to create a database table and let Flyways generate the corresponding database code.

Therefore, we go to the folder `TJ-Bot\database\src\main\resources\db` and add a new database migration script, incrementing the version. For example, if the script with the highest version number is `V1`, we will add `V2` to it. Give the script a nice name, such as `V2__Add_Questions_Table.sql`. The content is simply an SQL statement to create your desired table:
```sql
CREATE TABLE questions
(
    id       TEXT    NOT NULL PRIMARY KEY,
    response INTEGER NOT NULL
)
```
After adding this file, if you build or run the code (or simply execute `gradle database:build`), you will be able to use the database table.

#### Write database

Thanks to the jOOQ framework, writing to the database is now fairly simple. You can just use `database.write(...)` and make usages of the generated classes revolving around the `questions` table:
```java
try {
    database.write(context -> {
        QuestionsRecord questionsRecord = context.newRecord(Questions.QUESTIONS)
                .setId(id)
                .setResponse(clickedYes ? 1 : 0);
        if (questionsRecord.update() == 0) {
            questionsRecord.insert();
        }
    });

    event.reply("Saved response under '" + id + "'.").queue();
} catch (DatabaseException e) {
    event.reply("Sorry, something went wrong.").queue();
}
```
Trying it out, and we get the expected response:



#### Add logging