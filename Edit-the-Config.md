# Overview

In order to edit the configuration file of the bot, one has to login to the VPS and adjust the config file manually. Only members of the [Moderator](https://github.com/orgs/Together-Java/teams/moderators)-Team can do the following steps.

See [[Access the VPS]] for details of the login process.

# Guide

1. `ssh togetherjava` to login to the VPS
2. Either `cd ~/docker-infra/master-bot` or `cd ~/docker-infra/develop-bot` to go to the directory of the corresponding bot
3. Use `cd config`
4. Edit the `config.json` file, for example `vim config.json` or `nano config.json`
4. Save the file and [[restart the bot|Shutdown or restart of the bot]].