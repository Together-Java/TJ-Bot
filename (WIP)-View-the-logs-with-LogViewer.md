# Log-Viewer
This tutorial shows how to set up the module "logviewer". \
It's a Spring + Vaadin Web-Application in which you can see the Logs from your Bot in realTime.

## Setup

1. First you need a Discord app, you can use the same one you use for the Bot. \
If you don't have one yet, head over to the Tutorial for the Bot and create one. 
2. Open the [Applications Section](https://discord.com/developers/applications) from Discord 
3. Open [your Application](https://imgur.com/6N6JHDk)
4. Open the [OAuth2 Tab](https://imgur.com/XCAvBl1)
5. Add a [Redirect Link](https://imgur.com/xOhLbSB), e.g. `http://localhost:5050/login/oauth2/code/github`
6. Save [your changes](https://imgur.com/bYzMUX5)
7. Create a `config.json` File the directory of the logviewer module `{projectRoot}/logviewer/config.json` \
    Alternatively you can place it wherever you want and provide the Path to the file as a Start-Argument \
 The content of the file should be like this:
      ```json
      {
       "clientName": "DISCORD_APPNAME",
       "clientId": "DISCORD_OAUTH2_CLIENTID",
       "clientSecret": "DISCORD_OAUTH2_SECRET",
       "rootUserName": "YOUR_DISCORD_USERNAME",
       "rootDiscordID": "YOUR_DISCORD_ID",
       "logPath": "application/logs",
       "databasePath": "logviewer/db/db.db",
       "redirectPath": "http://localhost:5050/login/oauth2/code/github"
       }
      ```
      <details>
           <summary>Explanation for Parameter</summary>
   
   \
           `clientName` Is the Name of your Discord Application\
           `clientId` Is the ClientID you can [Copy in the OAuth2 Tab](https://imgur.com/x7mUyUW) from Discord\
           `clientSecret` Is the Secret you can [Copy in the OAuth2 Tab](https://imgur.com/YEJzMAS) \
           `rootUserName` Is your own Discord Username\
           `rootDiscordID` Is your own Discord ID, enable Developer Mode in your Discord App and use [Right-Click](https://imgur.com/z0FjqPC) on one of your own Posts\
           `logPath` is the path to the Logs from the Bot, not for this application\
           `databasePath` is the path where the DataBase for this Web-Application should be saved\
           `redirectPath` is the URL you gave in the Discord OAuth2 Settings
      </details>

8. You are done, start the Application. Open your browser on http://localhost:5050 and [accept the Authorization](https://imgur.com/I7s1alf).

## Quick overview

On the [left side](https://imgur.com/aKLgNRm) you can see 3 Views.

 - "Logs" displays the actual Logfiles like they are on the disk.
 - "Streamed" displays the Log-Event's your Bot did send to this Web-App
 - "User-Management" Enables adding/removing Users and managed the Roles of Users. (Right-Click)