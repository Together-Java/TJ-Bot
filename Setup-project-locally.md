# Overview

This tutorial shows how to download, setup and start the **TJ-Bot** project locally on your machine.

## Prerequisites
* [latest version of Java](https://adoptium.net/) installed
* your favorite Java IDE or text editor, e.g. [IntelliJ](https://www.jetbrains.com/idea/download/) or [Eclipse](https://www.eclipse.org/downloads/)
* [`git`](https://git-scm.com/downloads) installed (or any GUI or IDE plugin)
* [`gradle`](https://gradle.org/releases/) available (or any GUI or IDE plugin), you can either install it or use our provided wrapper
* your own [Discord](https://discord.com/)-Bot, tied to a server (see [[Create Discord server and bot]])
  * a token of that bot

## What you will learn
* use git to download the project
* use gradle to download dependencies
* use gradle to build the project
* connect your bot to the program
* use gradle to start the bot
* interact with the bot from your server

# Tutorial

## Clone repository

First of all, you have to download the project to your machine. Visit the projects [GitHub website](https://github.com/Together-Java/TJ-Bot) and copy the `.git` link, which is this
```
https://github.com/Together-Java/TJ-Bot.git
```
![.git link](https://i.imgur.com/8jGsr06.png)

### IntelliJ git plugin

IntelliJ comes by default with a `git` plugin. You can easily clone repositories to your disk by clicking a few buttons.

1. open your IntelliJ and select `Get from VSC`.
2. select `Git`, enter the `.git` link and select a directory for the project; smack that `Clone` button
3. IntelliJ will now open the project

![Get from VSC IntellIJ UI](https://i.imgur.com/uyqWyGF.png)
![.git url IntellIj UI](https://i.imgur.com/AEG0sqg.png)

### Manual usage of `git`

To download the project, use the following command:
```bash
git clone https://github.com/Together-Java/TJ-Bot.git TJ-Bot
```
You now have the project and all its data locally.

![git clone command line](https://i.imgur.com/EaLmolj.png)
![TJ-Bot folder](https://i.imgur.com/asBubhE.png)

## Gradle

Next up, you have to download all the dependencies, generate the database and build the project.

### IntelliJ Gradle plugin

IntelliJ comes by default with a `gradle` plugin. If not started already automatically, you can command it to do all of above by clicking a bunch of buttons.

1. open the Gradle view
2. expand the view and click on `TJ-Bot > Tasks > build > build`, or just click on the elephant icon and enter `gradle build`

![Gradle tasks IntelliJ UI](https://i.imgur.com/ziFdX9P.png)
![Gradle command IntelliJ UI](https://i.imgur.com/7OuyvMN.png)
![Gradle output](https://i.imgur.com/Q32x2qP.png)

### Manual usage of `gradle`

You can also just execute Gradle from the command line.

1. open a command line in the root directory of the project
2. execute `gradle build`

![Gradle command line start](https://i.imgur.com/YcVjVxZ.png)
![Gradle command line end](https://i.imgur.com/WGextPN.png)

## Start the bot

Last but not least, you want to start the bot with your bot token and let it connect to your private bot with which you can interact from one of your servers.

For this step, you need to hold your bot token ready, you can find it at the [Discord Developer Portal](https://discord.com/developers/applications).

See the following guide if you still have to create a server and a bot first:
* [[Create Discord server and bot]]

![Discord Developer Portal - Bot Token](https://i.imgur.com/IB5W8vZ.png)

To run the bot, you will need a `config.json` file with content like this:
```json
{
    "token": "<your_token_here>",
    "databasePath": "db/database.db",
    "projectWebsite": "https://github.com/Together-Java/TJ-Bot",
    "discordGuildInvite": "https://discord.com/invite/XXFUXzK"
}
```
Replace `<your_token_here>` with your bot token; you can also adjust the other settings if you want.

### IntelliJ

1. put the configuration file to `TJ-Bot\application\config.json` or run the program with a single argument, the path to your config file
2. in the Gradle view, click the `run` task and start it

![Bot runs](https://i.imgur.com/KdsSsx0.png)

### Command line, runnable jar

1. build a runnable jar of the project by executing `gradle shadowJar`  
  1.1. the jar can now be found at `TJ-Bot\application\build\libs`
2. unless you move the jar around, you have to adjust the database path in the config to `../../../build/database.db`
3. put the configuration file right next to the jar or run the program with a single argument, the path to your config file
4. run `java -jar TJ-Bot.jar`

![shadowJar](https://i.imgur.com/jGMVAv4.png)
![jar](https://i.imgur.com/Xv6HIFG.png)

### Have fun

The bot is now running and connected to your server, hurray 🎉

You can now execute commands and see the bot do its magic:

![Bot in Discord](https://i.imgur.com/TtYs0OZ.png)
![Bot in Console](https://i.imgur.com/z3QUSaz.png)
