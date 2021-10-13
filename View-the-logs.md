# Overview

There are two ways to read the logs of the bots:
* by using the **logviewer**
* by manually logging in to the VPS and looking up the log files

## Logviewer

1. Ask a [Moderator](https://github.com/orgs/Together-Java/teams/moderators)-Team to be granted access to the logviewer.
2. Visit the website https://togetherjava.duckdns.org
3. and login with your Discord account,
4. accept the authorization.

![discord login](https://i.imgur.com/im6EMzO.png)
![discord authorization](https://i.imgur.com/pceELaM.png)

5. You should now be able to see the logs in the logviewer 🎉

![logviewer example](https://i.imgur.com/5cuZI85.png)

### Quick overview

On the **left side** you can see three views (depending on your role, some might be missing).
* **Logs** displays the actual logfiles.
* **Streamed** displays the log-events as the main application sends them to the web application.
* **User-Management** enables adding or removing users who can access this website and editing their roles (right click the panel).

![side panel](https://i.imgur.com/RM1tCy6.png)
![user management](https://i.imgur.com/xCygzQZ.png)

## Manually viewing the files

In order to read the log files of the bots directly, one has to login to the VPS and command Docker to execute the corresponding task. Only members of the [Moderator](https://github.com/orgs/Together-Java/teams/moderators)-Team can do the following steps.

See [[Access the VPS]] for details of the login process.

# Guide

1. `ssh togetherjava` to login to the VPS
2. Either `cd ~/docker-infra/master-bot` or `cd ~/docker-infra/develop-bot` to go to the directory of the corresponding bot
3. Execute `docker-compose logs -f`
4. Hit <kbd>Ctrl</kbd> + <kbd>C</kbd> to stop

![cmd logs](https://i.imgur.com/TTciCaY.png)