# Welcome to the TJ-Bot project! ![](https://i.imgur.com/flystC6.png)

First off, thank you for considering contributing to TJ-Bot. :tada:

TJ-Bot is an open source project, and we love to receive contributions from our community — **you**! There are many ways to contribute, from writing tutorials, improving the documentation, submitting bug reports and feature requests or writing code which can be incorporated into TJ-Bot itself.

Following these guidelines helps to communicate that you respect the time of the developers managing and developing this open source project. In return, they should reciprocate that respect in addressing your issue, assessing changes, and helping you finalize your pull requests.

## Ground Rules

* Create [issues](https://github.com/Together-Java/TJ-Bot/issues) for any major changes and enhancements that you wish to make, as well as for reporting any sort of bugs. For more lighthearted talks, you can use [discussions](https://github.com/Together-Java/TJ-Bot/discussions). Discuss things transparently and get community feedback.
* Be welcoming to newcomers and encourage diverse new contributors from all backgrounds.

## Your First Contribution

Unsure where to begin contributing to TJ-Bot? You can start by looking through these labels!
* [good first issue](https://github.com/Together-Java/TJ-Bot/issues/?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) - issues which should only require a few lines of code, and a test or two.
* [help wanted](https://github.com/Together-Java/TJ-Bot/issues/?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)  - issues which should be a bit more involved than good first issues.

Working on your first Pull Request? You can check these resources:
* http://makeapullrequest.com/
* http://www.firsttimersonly.com/


At this point, you're ready to make your changes! Feel free to ask for help; everyone is a beginner at first! :tada: 


# Getting started

### Create an issue

Most code changes originate in creation of an [issue](https://github.com/Together-Java/TJ-Bot/issues), where you describe what you would like to change. While this step is generally recommended, it is not necessary.

### Create a feature-branch or fork

Then, you either create a `feature/name_of_your_feature` branch for your changes or a fork of the repository.

The repository has two main branches:
* `master`, a stable branch mostly used for releases that receives changes only ocasionally
* `develop`, the branch where the active development takes place; receives changes frequently

Your `feature`-branch will be based off the `develop` branch. The model we are using is a light version of what is called [Gitflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow):

![Gitflow illustration](https://i.imgur.com/P4mCPza.png)

### Create a pull request

When you are done, you will create a [pull request](https://github.com/Together-Java/TJ-Bot/pulls) to request feedback from the rest of the community. At this point, your code will be automatically tested against our [[code guidelines|Code Guidelines]] (Spotless, SonarCloud, CodeQL, and more).

Each pull request should be clear, concise and informative. Please consider checking these resources: [writing a great pull request](https://www.pullrequest.com/blog/writing-a-great-pull-request-description/) and [unwritten guide to pull requests](https://www.atlassian.com/blog/git/written-unwritten-guide-pull-requests).

A pull request should only implement one feature or bugfix. If you want to add or fix more than one thing, please submit another pull request.

### Merge your pull request

Once your code passed those checks and has been reviewed and **accepted by at least two members** of the community, you can merge it to the `develop` branch.

We require a *linear history*, that means you have two options to get your code merged:
* `squash`, merges all your commits together into a single commit, which will then be merged into the projects history
* `rebase`, puts your changes aside, updates the original code base you worked with and then puts your changes back on top of it, all your commits will be preserved and land in the projects history

From there on, it will lead to an automatic re-deployment of the bot on a test environment, where you can test out your changes live.

After a while, the `master` branch will be synced with `develop` again, leading to your changes finally being live on the real server!

# Community

You can chat with the TJ-Bot users and devs in our [discord server](https://discord.gg/togetherjava)!

Enjoy and have fun 👍