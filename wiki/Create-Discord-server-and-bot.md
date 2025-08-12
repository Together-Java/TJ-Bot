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
We use Discord's server template feature for this, this way you don't have to create all the channels, roles and more on your own.
You can still modify the servers channels and roles after creation, as it's only a template.

This can be done using the following [link](https://discord.new/WhtXEUZeFdTg).

1. Open the URL from above
2. Follow the dialog and enter details  
  2.1. Upload a picture  
  2.2. Enter a name  
  2.3. smack the **Create** button
3. boom! you have your own Discord server 🎉 

![Server details](https://user-images.githubusercontent.com/49957334/194017378-c2c2fb65-4235-41d9-ac23-673a9fa178c4.png)
![Server created](https://user-images.githubusercontent.com/49957334/194017750-1e9c1316-fef9-4718-9cd9-5f8dbf8dcaa0.png)


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
  4.4. enable the **Server Members Intent**  
  4.5. enable the **Message Content Intent**  
5. on the **OAuth** tab  
  5.1. select the `Bot` and `applications.commands` **Scope**s  
  5.2. select the desired **Bot permissions**, e.g. `Send Messages`, `Read Message History`, `Add Reactions`, `Use Slash Commands`  
  5.3. from the **Scope** section, copy the URL it generated, this is the **bots invite link**

![New Application](https://i.imgur.com/X1M7F0d.png)
![enter application name](https://i.imgur.com/pxRTzGc.png)
![enter application details](https://i.imgur.com/TvsyJTc.png)
![Add Bot](https://i.imgur.com/8jshb9M.png)
![Confirm add bot](https://i.imgur.com/vps9yLt.png)
![Token](https://i.imgur.com/l0UZPD3.png)
![Enable Intents](https://i.imgur.com/Hi4bkCZ.png)
![scopes](https://i.imgur.com/8x6WjDT.png)
![Bot permissions](https://github.com/Together-Java/TJ-Bot/assets/88111627/0dc2e9ec-3a84-4fef-ad23-d4f22cf2aadc)
![url](https://github.com/Together-Java/TJ-Bot/assets/88111627/7879ae7c-abc2-416c-bb11-50039361ef52)


## Add bot to server

Last but not least, you have to add the bot to the server you just created.

1. open the bots invite link URL in a browser  
  1.1. select your server to add the bot  
  1.2. click **Continue**  
  1.3. click **Authorize**
2. thats it, your bot was now added to the server! 🎉 

![Add bot](https://i.imgur.com/ceaemII.png)
![Authorize](https://i.imgur.com/239LT0n.png)
![bot added](https://i.imgur.com/jjPzxaZ.png)

# What next?

Now that have your own server and your own Discord bot and both are connected to each other, you can start to create or run an actual bot-program, such as TJ-Bot and give it your bots token!

Once the program has your token, it will connect to the bot and you can interact with it from your server.

You can learn about these steps in the following guide:
* [[Setup project locally]]

![bot example](https://i.imgur.com/TIewgLt.png)