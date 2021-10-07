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

Please refer to
* [[Add days command]] and
* [[Add question command]] respectively.