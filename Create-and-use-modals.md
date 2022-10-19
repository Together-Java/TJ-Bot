# Overview

This tutorial shows how to create and use **modals** in commands. That is, a popup message with a form that allows the user to input and submit data.

Please read [[Add a new command]] first.

## What you will learn
* create a modal
* react to a modal being submitted

# Tutorial

## Create a modal

To create a modal, all we need is a way to create [[Component IDs]]. The easiest way to do so is by extending `SlashCommandAdapter` or `BotCommandAdapter`. Alternatively, there is also the helper `ComponentIdInteractor`, which can be used directly.

We will create a very simple slash command that lets the user submit feedback, which is then logged in the console.

![command selection](https://i.imgur.com/IkNsefS.png)
![modal](https://i.imgur.com/QMV2Vrr.png)
![response](https://i.imgur.com/Pyrc25d.png)
![log](https://i.imgur.com/7z1IeKE.png)

The core of creating the model would be something like this:
```java
TextInput body = TextInput.create(MESSAGE_INPUT, "Message", TextInputStyle.PARAGRAPH)
        .setPlaceholder("Put your feedback here")
        .setRequiredRange(10, 200)
        .build();

// we need to use a proper component ID here
Modal modal = Modal.create(generateComponentId(), "Feedback")
        .addActionRow(body) // can also have multiple fields
        .build();

event.replyModal(modal).queue();
```

## React to modal submission

The system automatically forwards the event based on the generated component ID. To receive it, the class that send it has to implement `UserInteractor`. The easiest way for that is by implementing `BotCommand` or `SlashCommand`, ideally by extending the helpers `BotCommandAdapter` or `SlashCommandAdapter`.

This gives a method `onModalSubmitted` which will automatically be called by the system and can be used to react to the modal being submitted:

```java
@Override
public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
    String message = event.getValue(MESSAGE_INPUT).getAsString();
    System.out.println("User send feedback: " + message);

    event.reply("Thank you for your feedback!").setEphemeral(true).queue();
}

## Add to features
```
Finally, we have to add an instance of the class to the system. We do so in the file `Features.java`:

```java
features.add(new SendFeedbackCommand());
```

## Full code

The full code for the class is
```java
public final class SendFeedbackCommand extends SlashCommandAdapter {

    private static final String MESSAGE_INPUT = "message";

    public SendFeedbackCommand() {
        super("feedback", "Send feedback to the server maintainers", CommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        TextInput body = TextInput.create(MESSAGE_INPUT, "Message", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Put your feedback here")
                .setRequiredRange(10, 200)
                .build();

        Modal modal = Modal.create(generateComponentId(), "Feedback")
                .addActionRow(body)
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        String message = event.getValue(MESSAGE_INPUT).getAsString();
        System.out.println("User send feedback: " + message);

        event.reply("Thank you for your feedback!").setEphemeral(true).queue();
    }
}
```