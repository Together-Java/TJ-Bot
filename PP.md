# Privacy Policy

## Definitions

* "**TJ-Bot**" (also "**bot**") refers to the Discord bot that is subject under this policy
* "**[Together Java](https://github.com/orgs/Together-Java/teams/moderators/members)**" (also "**We**" or "**Us**") is the group of people responsible for the **TJ-Bot** product

## Preample

This Privacy Policy document contains types of information that is collected and recorded by **TJ-Bot** and how **Together Java** uses it.

If you have additional questions or require more information about **Together Java**'s Privacy Policy, do not hesitate to [contact us](#contact).

## General Data Protection Regulation (GDPR)

We are a Data Controller of your information.

**Together Java** legal basis for collecting and using the personal information described in this Privacy Policy depends on the Personal Information we collect and the specific context in which we collect the information:

* You have given **Together Java** permission to do so
* Processing your personal information is in **Together Java** legitimate interests
* **Together Java** needs to comply with the law

**Together Java** will retain your personal information only for as long as is necessary for the purposes set out in this Privacy Policy. We will retain and use your information to the extent necessary to comply with our legal obligations, resolve disputes, and enforce our policies.

If you are a resident of the European Economic Area (EEA), you have certain data protection rights. If you wish to be informed what Personal Information we hold about you and if you want it to be removed from our systems, please [contact us](#contact).

In certain circumstances, you have the following data protection rights:

* The right to access, update or to delete the information we have on you.
* The right of rectification.
* The right to object.
* The right of restriction.
* The right to data portability
* The right to withdraw consent

## Usage of Data

**TJ-Bot** may use stored data, as defined below, to offer different features and services. No usage of data outside of the aforementioned cases will happen and the data is not shared with any third-party site or service.

### Databases

**TJ-Bot** uses databases to store information about users, in order to provide its features and services. The database schemas are public source and can be viewed [here](https://github.com/Together-Java/TJ-Bot/tree/develop/application/src/main/resources/db).

The databases may store
* `user_id` of users (the unique id of a Discord account),
* `timestamp`s of actions (for example when a command has been used),
* `guild_id` of guilds the **bot** is member of (the unique id of a Discord guild),
* `channel_id` of channels belonging to guilds the **bot** is member of (the unique id of a Discord channel),
* `message_id` of messages send by users in guilds the **bot** is member of (the unique id of a Discord message),
*  `participant_count` of no of people who participated in help thread discussions,
*  `tags` aka categories to which these help threads belong to,
*  `timestamp`s for both when thread was created and closed,
*  `message_count` the no of messages that were sent in lifecycle of any help thread

_Note: Help threads are just threads that are created via forum channels, used for anyone to ask questions and get help
in certain problems._

and any combination of those.

For example, **TJ-Bot** may associate your `user_id` with a `message_id` and a `timestamp` for any message that you send in a channel belonging to guilds the **bot** is member of.

**TJ-Bot** may further store data that you explicitly provided for **TJ-Bot** to offer its services. For example the reason of a moderative action when using its moderation commands.

Furthermore, upon utilization of our help service, `user_id`s and `channel_id`s are stored to track when/how many questions a user asks. The data may be stored for up to **180** days. 

The stored data is not linked to any information that is personally identifiable.


No other personal information outside of the above mentioned one will be stored. In particular, **TJ-Bot** does not store the content of sent messages.

### Log Files

**TJ-Bot** follows a standard procedure of using log files. These files log users when they use one of the **bot**'s provided commands, features or services.

The information collected by log files include

* `user_name` of users (the nickname of a Discord account),
* `user_discrimator` of users (the unique discriminator of a Discord account),
* `user_id` of users (the unique id of a Discord account),
* `timestamp`s of actions (for example when a command has been used),
* `guild_id` of guilds the **bot** is member of (the unique id of a Discord guild),
* `channel_id` of channels belonging to guilds the **bot** is member of (the unique id of a Discord channel),
* `message_id` of messages send by users in guilds the **bot** is member of (the unique id of a Discord message).

The stored data is not linked to any information that is personally identifiable.

No other personal information outside of the above mentioned one will be stored. In particular, **TJ-Bot** does not store the content of sent messages.

The purpose of the information is for analyzing trends, administering the **bot**, and gathering demographic information.

### Temporarely stored Information

**TJ-Bot** may keep the stored information in an internal cacheing mechanic for a certain amount of time. After this time period, the cached information will be dropped and only be re-added when required.

Data may be dropped from cache pre-maturely through actions such as removing the **bot** from a Server.

## Removal of Data

Removal of the data can be requested through e-mail at [together.java.tjbot@gmail.com](mailto:together.java.tjbot@gmail.com).

For security reasons will we ask you to provide us with proof of ownership of the Server, that you wish the data to be removed of. Only a server owner may request removal of data and requesting it will result in the bot being removed from the Server, if still present on it.

## Third Party Privacy Policies

This Privacy Policy does not apply to other linked websites or service providers. Thus, we are advising you to consult the respective Privacy Policies of these third-party websites or services for more detailed information. It may include their practices and instructions about how to opt-out of certain options.

## Children's Information

Another part of our priority is adding protection for children while using the internet. We encourage parents and guardians to observe, participate in, and/or monitor and guide their online activity.

**TJ-Bot** does not knowingly collect any Personal Identifiable Information from children under the age of 13. If you think that your child provided this kind of information to our **bot**, we strongly encourage you to [contact us](#contact) immediately and we will do our best efforts to promptly remove such information from our records.

## Limitations

Our Privacy Policy applies only to **TJ-Bot** instances that are member of a Server owned by **Together Java**.

This policy is not applicable to any information collected by **bot** instances hosted by other parties, even if they have been build based on our official [source](https://github.com/Together-Java/TJ-Bot). This policy is also not applicable to any information collected via channels other than this **bot**, such as the data collected by linked websites.

## Contact

People may get in contact through e-mail at [together.java.tjbot@gmail.com](mailto:together.java.tjbot@gmail.com), or through **Together Java**'s [official Discord](https://discord.com/invite/XXFUXzK).

Other ways of support may be provided but are not guaranteed.
