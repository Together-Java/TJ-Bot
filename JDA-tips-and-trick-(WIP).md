
# Index

Since this project makes usage of JDA, I thought, why not write some tips and tricks done to (hopefully) make your lives easier?

## Tips

### Use [ISnowflake#getIdLong](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/ISnowflake.html#getIdLong()) instead of [ISnowflake#getId](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/ISnowflake.html#getId())
Internally JDA uses longs instead of strings, so they're faster and still work. \
Example:
```java
long userId = event.getUser().getIdLong();
```
In some cases using long is sub-optimal, example is when you're comparing it to a component ID. \
Component IDs are custom Strings allowing you to store data within the ID.
Example:
```java
String userThatClickedId = event.getUser().getId();
String userId = idArgs.get(0);


if (userThatClickedId.equals(userId)) {
    ...
}
```
If you made usage of longs instead it would add parsing that JDA can also do for you

### Don't forget `.queue();`!
Almost all Discord requests don't run automatically, and require a `.queue();` \
These are [RestActions](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/requests/restaction/package-summary.html) \
They require a `.queue();`, you'll see it within your IDE when it requires a queue. \
The code:
```java
event.getChannel().sendMessage("Yeah!");
```
Produces in IntelliJ

![](https://i.imgur.com/PPkUkdH.png)

I'd recommend it to try this out within your IDE, if it doesn't give a warning check your settings! \
It'll spare you a few hours trying to debug.

### There's a lot of RestAction types

Some things allow you to change more, like when editing a message. \
JDA can't add 500 options to the [TextChannel#editMessageById()](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/MessageChannel.html#editMessageById(long,net.dv8tion.jda.api.entities.Message)) method, this would make it really hard to maintain. \
So instead it returns a [MessageAction](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/requests/restaction/MessageAction.html) 
And this allows you to set components, and a lot more.

### Cast [JDA](https://github.com/discord/discord-api-docs/discussions/3581) to JDAImpl for more methods

Internally JDA uses JDAImpl instead of JDA, this has way more (internal) methods. \
While almost if not all are useless for you, some might be useful in specific use-cases. \
Feel free to ask for help in our Discord if you think you need one. \
**Breaking changes can happen any moment, and there are no docs**

### Every JDA related object has a getJDA method

It's as simple as the title, you're running an event and need a JDA instance? WhateverEvent#getJDA! \
You've a member and want a JDA instance? Member#getJDA!

## Tricks

Due to the size of JDA, you might do things in a way making it harder / longer.
Here you'll find some tricks to reduce size.

#### Method shortcuts

* [JDA#openPrivateChannelById](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/JDA.html#openPrivateChannelById(long)), this saves you from retrieving the user and running [User#openPrivateChannel](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/User.html#openPrivateChannel())
* [JDA#getGuildChannelById](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/JDA.html#getGuildChannelById(long)) also applies to textchannels, storechannels etc. You don't need a [Guild](https://ci.dv8tion.net/job/JDA/javadoc/net/dv8tion/jda/api/entities/Guild.html) instance to get channels.