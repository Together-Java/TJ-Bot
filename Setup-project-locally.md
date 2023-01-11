# Overview

This tutorial shows how to download, setup and start the **TJ-Bot** project locally on your machine.

Alternatively, you can also work directly in the cloud, for free, and get started in just a few seconds. See:
* [[Code in the cloud (codespaces)]]

## Prerequisites
* [Java 18](https://adoptium.net/temurin/releases?version=18) installed
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

1. open your IntelliJ and select `Get from VCS`.
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

<details>
<summary>ℹ️ If you get any gradle errors...</summary>
Make sure that your project and gradle is setup to use the latest Java version. Sometimes IntelliJ might guess it wrong and mess up, leading to nasty issues.

Therefore, review your **Project Structure** settings and the **Gradle** settings:
![project settings](https://i.imgur.com/2hPB4ga.png)
![gradle settings](https://i.imgur.com/O8FGHK0.png)
</details> 

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

To run the bot, you will need a `config.json` file with specific content. You can find a template for this file, with meaningful default values, in `application/config.json.template`.

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

![Bot command](https://user-images.githubusercontent.com/73871477/194744735-562b70a6-62ff-4675-9f04-e4327b38b2f6.png)
