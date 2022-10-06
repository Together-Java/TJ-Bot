# Overview

There are two ways to read the logs of the bots:
* by reading the forwarded messages in **Discord**
* by manually logging in to the **VPS** and looking up the log files

## Discord

All log messages, with a few sensitive exceptions, are forwarded to Discord via webhooks. You can read them in the two channels:

* **tjbot_log_info** - contains all `INFO`, `DEBUG`, `TRACE` messages
* **tjbot_log_error** - contains all `WARN`, `ERROR`, `FATAL` messages

![log channels](https://i.imgur.com/nkvy80n.png)

## Manually viewing the files

In order to read the log files of the bots directly, one has to login to the VPS and command Docker to execute the corresponding task. Only members of the [Moderator](https://github.com/orgs/Together-Java/teams/moderators)-Team can do the following steps.

See [[Access the VPS]] for details of the login process.

# Guide

1. `ssh togetherjava` to login to the VPS
2. Either `cd ~/docker-infra/master-bot` or `cd ~/docker-infra/develop-bot` to go to the directory of the corresponding bot
3. Execute `docker-compose logs -f`
4. Hit <kbd>Ctrl</kbd> + <kbd>C</kbd> to stop

![cmd logs](https://i.imgur.com/TTciCaY.png)