# Overview

This tutorial shows how to create your own Discord server and a Discord bot, which can then be connected to a program, like the TJ-Bot.

## Prerequisites
* a [Discord](https://discord.com/) account

## What you will learn
* create your own Discord server
* create a Discord bot
* add the bot to your server

# Tutorial

## Discord Server

As first step, you need to create your own Discord server. This is surprisingly easy.

1. click the `+` button on the server overview
2. follow the dialog and enter details, you may use the following details  
  2.1. click **Create My Own**  
  2.2. click **For me and my friends**  
  2.3. enter a name and upload a picture  
  2.4. smack that **Create** button  
3. boom! you have your own Discord server 🎉 

![Add server](https://i.imgur.com/RC4x989.png)
![Create My Own](https://i.imgur.com/Jfl3oqR.png)
![For me and my friends](https://i.imgur.com/ZEKQqd8.png)
![Server details](https://i.imgur.com/otOB7rL.png)
![server created](https://i.imgur.com/gBjTrZx.png)

## Discord Bot

Next up, you want to create your own bot.

1. visit the [Discord Developer Portal](https://discord.com/developers/applications)
2. click on **New Application**  
  2.1. enter the name for the bot
3. on the **General Information** tab  
  3.1. enter a name, description and upload a picture
  3.2. hit **Save Changes**
4. on the **Bot** tab  
  4.1. click on **Add Bot**  
  4.2. hit the **Yes, do it!** button  
  4.3. you can now see your bots **Token**, you will need this when connecting the bot to a program later
5. on the **OAuth** tab
  5.1. select the `Bot` and `applications.commands` **Scope**s  
  5.2. select the desired **Bot permissions**, e.g. `Send Messages`, `Read Message History`, `Add Reactions`, `Use Slash Commands`  
  5.3. from the **Scope** section, copy the URL it generated, this is the **bots invite link**

![New Application](https://i.imgur.com/X1M7F0d.png)
![enter application name](https://i.imgur.com/pxRTzGc.png)
![enter application details](https://i.imgur.com/TvsyJTc.png)
![Add Bot](https://i.imgur.com/8jshb9M.png)
![Confim add bot](https://i.imgur.com/vps9yLt.png)
![Token](https://i.imgur.com/l0UZPD3.png)
![scopes](https://i.imgur.com/8x6WjDT.png)
![Bot permissions](https://i.imgur.com/32Rl6k4.png)
![url](https://i.imgur.com/j7yVKeM.png)

## Add bot to server

Last but not least, you have to add the bot to the server you just created.

1. open the bots invite link URL in a browser  
  6.1. select your server to add the bot
  6.2. click **Continue**
  6.3. click **Authorize**
2. thats it, your bot was not added to the server! 🎉 

![Add bot](https://i.imgur.com/ceaemII.png)
![Authorize](https://i.imgur.com/239LT0n.png)
![bot added](https://i.imgur.com/jjPzxaZ.png)

# What next?

Now that have your own server and your own Discord bot and both are connected to each other, you can start to create or run an actual bot-program, such as TJ-Bot and give it your bots token!

Once the program has your token, it will connect to the bot and you can interact with it from your server.

You can learn about these steps in the following guide:
* [[Setup project locally]]

![bot example](https://i.imgur.com/TIewgLt.png)