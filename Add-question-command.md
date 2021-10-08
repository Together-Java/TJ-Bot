# Overview

This tutorial shows how to add a custom command, the `question` command:
* `/question ask <id> <question>`, `/question get <id>`
  * asks a question and users can click a `Yes` or `No` button
  * the choice will be saved in the database from which it can be retrieved using the `get` subcommand
  * e.g. `/question ask "noodles" "Do you like noodles?"` and `/question get "noodles"`

Please read [[Add a new command]] and [[Add days command]] first.

## What you will learn
* add a custom command
* reply to messages
* add sub-commands to a command
* add options (arguments) to a sub-command
* ephemeral messages (only visible to one user)
* add buttons to a message
* memorize data inside a button
* react to button click
* disable buttons from an old message
* create a new database table and migrate it
* read and write from/to a database (using Flyway, jOOQ and SQLite)
* basic logging (using SLF4J)

# Tutorial

## Setup

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

## Add sub-commands

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

## Try it out

At this point, we should try out the code. Do not forget to use `/reload` before though. You should now be able to use `/question ask` with two required options and `/question get` with only one required option. And the bot should respond back correspondly.

![question sub commands](https://i.imgur.com/3MI6ITN.png)

## `Ask` sub-command

### Add buttons

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

### React to button click

In order to react to a button click, we have to give an implementation for the `onButtonClick(...)` method, which `SlashCommandAdapter` already implemented, but without any action. The method provides us with the `ButtonClickEvent` and also with a `List<String>` of arguments, which are the optional arguments added to the **component id** earlier. In our case, we added the question id, so we can also retrieve it back now by using `args.get(0)`. Also, we can figure out which button was clicked by using `event.getButton().getStyle()`.

A minimal setup could now look like:
```java
@Override
public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
    String id = args.get(0);
    ButtonStyle buttonStyle = event.getButton().getStyle();

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

### Disable buttons after click

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

### Setup database

Last but not least for the `ask` command, we have to save the response in the database. Before we can get started with this, we have to create a database table and let Flyway generate the corresponding database code.

Therefore, we go to the folder `TJ-Bot\database\src\main\resources\db` and add a new database migration script, incrementing the version. For example, if the script with the highest version number is `V1`, we will add `V2` to it. Give the script a nice name, such as `V2__Add_Questions_Table.sql`. The content is simply an SQL statement to create your desired table:
```sql
CREATE TABLE questions
(
    id       TEXT    NOT NULL PRIMARY KEY,
    response INTEGER NOT NULL
)
```
After adding this file, if you build or run the code (or simply execute `gradle database:build`), you will be able to use the database table.

## Write database

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

![question ask write db](https://i.imgur.com/RP4rMGA.png)

### Add logging

At this point, we should add logging to the code to simplify debugging. Therefore, just add
```java
private static final Logger logger = LoggerFactory.getLogger(QuestionCommand.class);
```
to the top, as a new field for our class.

Now, you can use the logger wherever you want, for example to log a possible error message during writing the database:
```java
} catch (DatabaseException e) {
    logger.error("Failed to save response for '{}'", id, e);
    event.reply("Sorry, something went wrong.").queue();
}
```

The ask sub-command should now be working correctly.

## `Get` sub-command

This command is simpler as we do not have any dialog. We simply have to lookup the database and respond with the result, if found.

### Read database

Reading the database revolves around the `database.read(...)` methods and using the generated classes for the `questions` table:
```java
OptionalInt response = database.read(context -> {
    try (var select = context.selectFrom(Questions.QUESTIONS)) {
        return Optional.ofNullable(
                        select.where(Questions.QUESTIONS.ID.eq(id)).fetchOne())
                .map(QuestionsRecord::getResponse)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }
});
```

### Reply

The last part will be to reply with the saved response:

```java
if (response.isEmpty()) {
    event.reply("There is no response saved for the id '" + id + "'.")
            .setEphemeral(true)
            .queue();
    return;
}

boolean clickedYes = response.getAsInt() != 0;
event.reply("The response for '" + id + "' is: " + (clickedYes ? "Yes" : "No")).queue();
```

The full code for the `handleGetCommand` method is now:
```java
String id = event.getOption("id").getAsString();

try {
    OptionalInt response = database.read(context -> {
        try (var select = context.selectFrom(Questions.QUESTIONS)) {
            return Optional.ofNullable(
                            select.where(Questions.QUESTIONS.ID.eq(id)).fetchOne())
                    .map(QuestionsRecord::getResponse)
                    .map(OptionalInt::of)
                    .orElseGet(OptionalInt::empty);
        }
    });
    if (response.isEmpty()) {
        event.reply("There is no response saved for the id '" + id + "'.")
                .setEphemeral(true)
                .queue();
        return;
    }

    boolean clickedYes = response.getAsInt() != 0;
    event.reply("The response for '" + id + "' is: " + (clickedYes ? "Yes" : "No")).queue();
} catch (DatabaseException e) {
    logger.error("Failed to get response for '{}'", id, e);
    event.reply("Sorry, something went wrong.").setEphemeral(true).queue();
}
```
and if we try it out, we see that the command works:

![question get](https://i.imgur.com/lQK3eGF.png)

## Full code

After some cleanup and minor code improvements, the full code for `QuestionCommand` is:

```java
package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.DatabaseException;
import org.togetherjava.tjbot.db.generated.tables.Questions;
import org.togetherjava.tjbot.db.generated.tables.records.QuestionsRecord;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * This creates a command called {@code /question}, which can ask users questions, save their
 * responses and retrieve back responses.
 * <p>
 * For example:
 *
 * <pre>
 * {@code
 * /question ask id: noodles question: Do you like noodles?
 * // User clicks on a 'Yes' button
 *
 * /question get id: noodles
 * // TJ-Bot: The response for 'noodles' is: Yes
 * }
 * </pre>
 */
public final class QuestionCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(QuestionCommand.class);
    private static final String ASK_SUBCOMMAND = "ask";
    private static final String GET_SUBCOMMAND = "get";
    private static final String ID_OPTION = "id";
    private static final String QUESTION_OPTION = "question";
    private static final String NAME = "question";
    private final Database database;

    /**
     * Creates a new question command, using the given database.
     *
     * @param database the database to store the responses in
     */
    public QuestionCommand(Database database) {
        super(NAME, "Asks users questions, responses are saved and can be retrieved back",
                SlashCommandVisibility.GUILD);
        this.database = database;

        getData().addSubcommands(
                new SubcommandData(ASK_SUBCOMMAND,
                        "Asks the users a question, responses will be saved")
                            .addOption(OptionType.STRING, ID_OPTION,
                                    "Unique ID under which the question should be saved", true)
                            .addOption(OptionType.STRING, QUESTION_OPTION, "Question to ask", true),
                new SubcommandData(GET_SUBCOMMAND, "Gets the response to the given question")
                    .addOption(OptionType.STRING, ID_OPTION,
                            "Unique ID of the question to retrieve", true));
    }

    private static int clickedYesToInt(boolean clickedYes) {
        return clickedYes ? 1 : 0;
    }

    private static boolean isClickedYesFromInt(int clickedYes) {
        return clickedYes != 0;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case ASK_SUBCOMMAND -> handleAskCommand(event);
            case GET_SUBCOMMAND -> handleGetCommand(event);
            default -> throw new AssertionError();
        }
    }

    private void handleAskCommand(@NotNull CommandInteraction event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        String question = Objects.requireNonNull(event.getOption(QUESTION_OPTION)).getAsString();

        event.reply(question)
            .addActionRow(Button.of(ButtonStyle.SUCCESS, generateComponentId(id), "Yes"),
                    Button.of(ButtonStyle.DANGER, generateComponentId(id), "No"))
            .queue();
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
        String id = args.get(0);
        ButtonStyle buttonStyle = Objects.requireNonNull(event.getButton()).getStyle();

        boolean clickedYes = switch (buttonStyle) {
            case DANGER -> false;
            case SUCCESS -> true;
            default -> throw new AssertionError("Unexpected button action clicked: " + buttonStyle);
        };

        event.getMessage()
            .editMessageComponents(ActionRow
                .of(event.getMessage().getButtons().stream().map(Button::asDisabled).toList()))
            .queue();

        try {
            database.write(context -> {
                QuestionsRecord questionsRecord = context.newRecord(Questions.QUESTIONS)
                    .setId(id)
                    .setResponse(clickedYesToInt(clickedYes));
                if (questionsRecord.update() == 0) {
                    questionsRecord.insert();
                }
            });

            event.reply("Saved response under '" + id + "'.").queue();
        } catch (DatabaseException e) {
            logger.error("Failed to save response for '{}'", id, e);
            event.reply("Sorry, something went wrong.").queue();
        }
    }

    private void handleGetCommand(@NotNull CommandInteraction event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();

        try {
            OptionalInt response = database.read(context -> {
                try (var select = context.selectFrom(Questions.QUESTIONS)) {
                    return Optional
                        .ofNullable(select.where(Questions.QUESTIONS.ID.eq(id)).fetchOne())
                        .map(QuestionsRecord::getResponse)
                        .map(OptionalInt::of)
                        .orElseGet(OptionalInt::empty);
                }
            });
            if (response.isEmpty()) {
                event.reply("There is no response saved for the id '" + id + "'.")
                    .setEphemeral(true)
                    .queue();
                return;
            }

            boolean clickedYes = isClickedYesFromInt(response.getAsInt());
            event.reply("The response for '" + id + "' is: " + (clickedYes ? "Yes" : "No")).queue();
        } catch (DatabaseException e) {
            logger.error("Failed to get response for '{}'", id, e);
            event.reply("Sorry, something went wrong.").setEphemeral(true).queue();
        }
    }
}
```