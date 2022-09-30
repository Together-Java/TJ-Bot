# Overview

This tutorial shows how to add custom **context command** to the bot. That is, a command that can be selected when **right clicking** an user or message.

Please read [[Add a new command]] first.

## What you will learn
* add a custom user context command
* add a custom message context command
* reply to messages

# Tutorial

## User-context command

To create a command that can be selected when right clicking a user, we have to implement the `UserContextCommand` interface. The class `BotCommandAdapter` simplifies this process heavily.

We will create a very simple command that just greets an user:

![command selection](https://i.imgur.com/IzFvYva.png)
![greet user](https://i.imgur.com/Wo2QzVC.png)

The code is really simple:
```java
public final class HelloUserCommand extends BotCommandAdapter implements UserContextCommand {

    public HelloUserCommand() {
        super(Commands.user("say-hello"), CommandVisibility.GUILD);
    }

    @Override
    public void onUserContext(UserContextInteractionEvent event) {
        event.reply("Hello " + event.getTargetMember().getAsMention()).queue();
    }
}
```
Finally, we have to add an instance of the class to the system. We do so in the file `Features.java`:

```java
features.add(new HelloUserCommand());
```

## Message-context command

To create a command that can be selected when right clicking a message, we have to implement the `MessageContextCommand` interface. `BotCommandAdapter` helps us out here again.

We will create a very simple command that just repeats the given message:

![command selection](https://i.imgur.com/Lm5gbqZ.png)
![repeat message](https://i.imgur.com/o4NNcP0.png)

The code is very similar:
```java
public final class RepeatMessageCommand extends BotCommandAdapter implements MessageContextCommand {

    public RepeatMessageCommand() {
        super(Commands.message("repeat"), CommandVisibility.GUILD);
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        String content = event.getTarget().getContentRaw();
        event.reply(content).queue();
    }
}
```
And we add it to `Features.java` as well:

```java
features.add(new RepeatMessageCommand());
```