# Overview

This guide gives lists some tips and tricks to ease the life of developers working with JDA, the Discord framework used by this project.

## Tips

### Use [ISnowflake#getIdLong](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/ISnowflake.html#getIdLong()) instead of [ISnowflake#getId](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/ISnowflake.html#getId())

Internally JDA uses `long`s instead of `String`s, so they are faster and still work.

Example:
```java
long userId = event.getUser().getIdLong();
```
However, in some cases using long is sub-optimal, for example when comparing it to a component ID. Component IDs are custom strings allowing storing data within the ID.
Example:
```java
String userThatClickedId = event.getUser().getId();
String userId = idArgs.get(0);

if (userThatClickedId.equals(userId)) {
    ...
}
```
If you already have a `long` instead though, JDA can still do it, but it requires some extra efforts.

### Don't forget `.queue();`

Almost all Discord requests do not run automatically and require an explicit `.queue();`.

Affected requests are called [RestActions](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/requests/restaction/package-summary.html). The rule of thumb is, if your message returns a result it likely has to be queued. Most IDEs can detect such a situation, as seen in the following example:

![IntelliJ queue warning](https://i.imgur.com/PPkUkdH.png)

### There are lot of `RestAction` types

Some of the many `RestAction` types give you more flexibility and additional functionality.

For example, when editing a message, you can not just add 500 options to the [TextChannel#editMessageById()](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/MessageChannel.html#editMessageById(long,net.dv8tion.jda.api.entities.Message)) method. Instead, it returns a [MessageAction](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/requests/restaction/MessageAction.html) object, which allows you to set all the components and more.

### Every JDA related object has a `getJDA` method

Whenever you need an instance of `JDA`, the framework got you covered and offers a general `getJDA()` method available on pretty much any JDA related object.

### Cast [JDA](https://github.com/discord/discord-api-docs/discussions/3581) to `JDAImpl` for more methods

This is a dangerous tip and we advise you to not consider it unless there is really no other option. If you are unsure, please ask the other developers for help.

Internally JDA uses `JDAImpl` instead of `JDA`, which has way more _(internal)_ methods. While almost, if not all, of them are probably not relevant, some might prove useful in very specific use-cases.

Since this is an internal API, breaking changes can happen with any new version of JDA and it also has no documentation.

## Tricks

Due to the complexity of JDA, you might easily run into a situation where you solve a problem in a certain but not optimal way that is either overly complex or just very lengthy. This chapter shows some tricks to help you use JDA correct and better.

### Method shortcuts

JDA offers some shortcuts to methods and patterns frequently used:
* [JDA#openPrivateChannelById](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/JDA.html#openPrivateChannelById(long)), instead of manually retrieving the user and calling [User#openPrivateChannel](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/User.html#openPrivateChannel())
* [JDA#getGuildChannelById](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/JDA.html#getGuildChannelById(long)) also applies to _textchannels_, _storechannels_ and more. So a [Guild](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/Guild.html) instance is not required to get channels.