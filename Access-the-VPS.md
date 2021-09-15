# Overview

The bot is hosted on a Virtual Private Server (VPS) by [Hetzner](https://www.hetzner.com/). The machine can be reached under the DDNS `togetherjava.duckdns.org`.

Access to it is usually granted only to members of the [Moderator-Team](https://github.com/orgs/Together-Java/teams/moderators).

# Guide

In order to get access to the machine, the following steps have to be followed:

1. Generate a private-/public-key pair for [SSH](https://en.wikipedia.org/wiki/Secure_Shell). This can be done by executing the following command:
```batch
ssh-keygen -t ed25519 -C "your_email@address.here" -f ~/.ssh/together-java-vps
```
2. Put the key pair into your `.ssh` folder (Windows `C:\Users\YourUserNameHere\.ssh`, Linux `~/.ssh`)
3. Give the **public** key to someone who has access already  
  3.1. The person has to add your public key to the file `~/.ssh/authorized_keys`
4. Add the following entry to your `.ssh/config` file:
```
Host togetherjava
HostName togetherjava.duckdns.org
IdentityFile ~/.ssh/together-java-vps
User root
Port 22
```
5. Connect to the machine by using the command `ssh togetherjava`, you should get a response similar to:
![](https://i.imgur.com/eCyJVEt.png)
6. Congrats :tada:, you are now logged in. Once you are done, close the connection using `logout`.