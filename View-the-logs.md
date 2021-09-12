# Overview

In order to read the logs of the bots, one has to login to the VPS and command Docker to execute the corresponding task. Only members of the [Moderator](https://github.com/orgs/Together-Java/teams/moderators)-Team can do the following steps.

See [[How to access the VPS]] for details of the login process.

# Guide

1. `ssh togetherjava` to login to the VPS
2. Either `cd ~/docker-infra/master-bot` or `cd ~/docker-infra/develop-bot` to go to the directory of the corresponding bot
3. Execute `docker-compose logs -f`
4. Hit <kbd>Ctrl</kbd> + <kbd>C</kbd> to stop