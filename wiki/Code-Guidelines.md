# Overview

We want the project to be easy to understand and maintain, for anyone, including newcomers. Because of that, we enforce a strict code style based on popular and commonly used configurations. Additionally, we require the code to pass certain checks that analyze it with respect to maintainability, readability, security and more.

All pull requests have to pass those automated checks before they can be merged to the project.

Here is a quick glimpse at how our code usually looks like:

![](https://i.imgur.com/yp6fycB.png)

## Code style (Spotless)

The style and layout of the code is checked by **Spotless**. Its configuration is based on the commonly used [Google Java Style](https://google.github.io/styleguide/javaguide.html). The exact configuration being used can be found in the project at
```
TJ-Bot\meta\formatting\google-style-eclipse.xml
```

In order to check your code locally, you can either run **Spotless** or import the style into the formatter of the IDE of your choice. We tested the configuration with:
* IntelliJ
* Eclipse
* Visual Studio Code

### Run Spotless

Executing Spotless manually can be done via the Gradle task `spotlessApply`, which will automatically reformat your code according to the style.

Additionally, Spotless is configured to be executed automatically whenever you compile your code with Gradle, i.e. it is tied to the `compileJava` task.

## Static code analysis (SonarCloud)

In order to ensure that code is clean, readable and maintainable, we use static code analysis provided by **SonarCloud**.

In order to check your code locally, you can either run **SonarCloud** via a Gradle task or install a plugin for your favorite IDE, e.g. the [SonarLint](https://plugins.jetbrains.com/plugin/7973-sonarlint) plugin for IntelliJ.

### Run SonarCloud

Executing SonarCloud manually can be done via the Gradle task `sonarqube`, which will check the whole code and explain any issues it found in detail.

Additionally, SonarCloud is configured to be executed automatically whenever you build your code with Gradle, i.e. it is tied to the `build` task.