# Overview

This tutorial shows how to code and run the project in GitHubs cloud.

The service is completely free and allows you to get started in just a few seconds.

This approach is an alternative to setting the project up on your local machine, as explained in:
* [[Setup project locally]]

# Tutorial

## Create codespace

1. Visit the [landing page](https://github.com/Together-Java/TJ-Bot)
2. Click on `Code > Create codespace on develop`

![create codespace](https://i.imgur.com/Jg5jiXu.png)

GitHub now automatically sets up your codespace. The codespace is essentially a virtual machine with everything installed and configured that you need to work with this project.

Mostly, it will install Java and Gradle for you, and a few other useful plugins and extensions.

![setup](https://i.imgur.com/8zrXOTc.png)

This process takes about 2 minutes for the first time. Once the setup is done, it opens an instance of Visual Studio Code in your browser, with the project opened. You can now get started!

![VSC opened](https://i.imgur.com/Zb6trQb.png)

## Config

Before you can run the bot, you have to adjust the configuration file. Therefore, open the file `application/config.json`.

By default, it will be filled with some example values and most of them are totally okay for now.

The most important setting you have to change is the bot token. This enables the code to connect to your private bot with which you can then interact from your private server.

You can find the token at the [Discord Developer Portal](https://discord.com/developers/applications).

See the following guide if you still have to create a server and a bot first:
* [[Create Discord server and bot]]

![Discord Developer Portal - Bot Token](https://i.imgur.com/IB5W8vZ.png)

Replace `<your_token_here>` with your bot token; you can also adjust the other settings if you want.

![replace token](https://i.imgur.com/eCWZHSR.png)

## Run

Once done, you are good to go and can run the bot. Just enter `gradle application:run` in your terminal.

![gradle run](https://i.imgur.com/hQqq6DC.png)

On the first run, this might take around 3 minutes, because it will first have to download all dependencies, generate the database and compile the code.

Once the terminal reads `[main] INFO  org.togetherjava.tjbot.Application - Bot is ready`, you are done!

## Have fun

The bot is now running and connected to your server, hurray 🎉

You can now execute commands and see the bot do its magic:

![pong](https://i.imgur.com/0x3GsnU.png)

# IntelliJ instead of VSC

If you prefer IntelliJ, they offer a client called [JetBrains Gateway](https://www.jetbrains.com/remote-development/gateway/).

While not being all-within your browser, it is essentially an IDE that you can install, which will just remote-connect to your codespace.

Once installed, you have to get the **GitHub Codespaces** plugin:

![plugin](https://i.imgur.com/VKzLMd9.png)

You can then login to your GitHub account and select your codespace:

![select codespace](https://i.imgur.com/u9OVwXR.png)

The initial setup takes a few minutes, since it has to install IntelliJ on the codespace first.