# Logviewer

The **logviewer** module is a _Spring + Vaadin Web-Application_ in which one can see the logs written by the applications in real-time.

![logviewer example](https://i.imgur.com/5cuZI85.png)

In the following, we explain how to set the website up and how to host it.

## Setup

1. First you need a _Discord application_, you can use the same also used for the main application. See [[Create Discord server and bot]] for details on how to create one.
2. Open the [Applications Section](https://discord.com/developers/applications) from Discord 
3. Open **your Application**
4. Open the **OAuth2 Tab**
5. Add a **Redirect Link**, e.g. `https://localhost:443/login/oauth2/code/github`
6. Save your changes
7. Create a `config.json` file in the directory of the logviewer module `TJ-Bot/logviewer/config.json`. Alternatively you can place it wherever you want and provide the path to the file as a start-argument. The content of the file should be like this (fill in the Discord data):
```json
{
  "clientName": "<DISCORD_APPNAME>",
  "clientId": "<DISCORD_OAUTH2_CLIENTID>",
  "clientSecret": "<DISCORD_OAUTH2_SECRET>",
  "rootUserName": "<YOUR_DISCORD_USERNAME>",
  "rootDiscordID": "<YOUR_DISCORD_ID>",
  "logPath": "application/logs",
  "databasePath": "logviewer/db/db.db",
  "redirectPath": "https://localhost:443/login/oauth2/code/github"
}
```
#### Explanation for the parameters

* `clientName` is the name of your Discord Application
* `clientId` is the clientId you can [copy in the OAuth2 Tab](https://i.imgur.com/x7mUyUW.png) from Discord
* `clientSecret` is the secret you can [copy in the OAuth2 Tab](https://i.imgur.com/YEJzMAS.png)
* `rootUserName` is your own Discord username
* `rootDiscordID` is your own Discord ID, enable Developer Mode in your Discord App and [right-click](https://i.imgur.com/z0FjqPC.png) on one of your own posts
* `logPath` is the path to the logs from the Bot, not for this application
* `databasePath` is the path where the database for this Web-Application should be saved
* `redirectPath` is the URL you used in the Discord OAuth2 Settings

8. You are done, start the application. Open your browser on https://localhost:443 and **accept the Authorization**.

![your application](https://i.imgur.com/6N6JHDk.png)
![OAuth2 Tab](https://i.imgur.com/XCAvBl1.png)
![redirect URL](https://i.imgur.com/xOhLbSB.png)
![save changes](https://i.imgur.com/bYzMUX5.png)
![accept authorization](https://i.imgur.com/I7s1alf.png)

## Quick overview

On the **left side** you can see three views.
* **Logs** displays the actual logfiles as they are in the configured directories.
* **Streamed** displays the log-events as the main application sends them to the web application.
* **User-Management** enables adding or removing users who can access this website and editing their roles (right click the panel).

![side panel](https://i.imgur.com/RM1tCy6.png)
![user management](https://i.imgur.com/xCygzQZ.png)