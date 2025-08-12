# Overview

This tutorial shows how to connect SonarLint extension to our SonarCloud.

[SonarCloud](https://www.sonarsource.com/products/sonarcloud/) is a cloud-based static analysis tool we integrated in our CI/CD pipeline. It analyses code in each PR for bugs, vulnerabilities and code smells, and reports issues for the contributor to fix.

If you want to have your code analysed locally, as you write it, you want to install [SonarLint](https://www.sonarsource.com/products/sonarlint/) extension for your IDE.

Immediate feedback is important, as it increases your productivity. You can see issues immediately as you write code, and you can easily fix them. Having to push to trigger the analysis every time is cumbersome. You have to wait for the results, and then write and push the fix, and what if the fix has some issues as well?

The issue is, even with SonarLint, you might encounter these workflow issues, since SonarLint is not as powerful as SonarCloud and doesn't have all of our rules enabled. So the goal of this tutorial is to mitigate that as much as possible, and connect the local SonarLint extension to the SonarCloud.

## Prerequisites
* IDE or code editor supported by SonarLint: [IntelliJ](https://www.jetbrains.com/idea/), [Eclipse](https://eclipseide.org/) or [VSCode](https://code.visualstudio.com/)
* SonarLint extension. You can find them in marketplaces: [IntelliJ extension](https://plugins.jetbrains.com/plugin/7973-sonarlint), [Eclipse extension](https://marketplace.eclipse.org/search/site/SonarLint) 

## What you will learn
* Connect SonarLint to our SonarCloud

## Benefits

When SonarLint works in connected mode, it can:

* use the same quality profile (same rules activation, parameters, severity, ...)
* reuse some settings defined on the server (rule exclusions, analyzer parameters, ...)
* suppress issues that are marked as Won’t Fix or False Positive on the server

# Setting up Connected Mode

## Login to SonarCloud

If you don't have an account, use OAuth with your github account to [login](https://sonarcloud.io/sessions/new).

## Create a User Token

For connecting to SonarCloud, we will use a User Token, as it is the most secure way to connect to the SonarCloud.

Go to your SonarCloud [account security settings](https://sonarcloud.io/sessions/new), and generate new token.

## Quick IntelliJ guide

1. Go to: _File | Settings | Tools | SonarLint_
2. Click on _+_, or press _Alt + Insert_
3. Enter connection name, for example 'TJ' and click *Next* (or press *return*)
4. Enter the token you just created and click *Next* 
5. Click *'Select another organization..'* and enter `togetherjava`, click *OK* and then *Next*
6. Click *Next* again if you are happy with notifications settings
7. Click *Next* once again
8. Go to: _File | Settings | Tools | SonarLint | Project Settings_
9. Check _'Bind project to SonarQube / SonarCloud'_
10. Select previously created connection in *'Connection:'* dropdown menu
11. Click *'Search in list...'* button, and click *OK*; or enter project key manually: `Together-Java_TJ-Bot`
12. Click *Apply*

## IDE-specific instructions

Follow these official tutorials for your IDE:

* InteliJ - https://github.com/SonarSource/sonarlint-intellij/wiki/Bind-to-SonarQube-or-SonarCloud

* Eclipse - https://github.com/SonarSource/sonarlint-eclipse/wiki/Connected-Mode

* VSCode - https://github.com/SonarSource/sonarlint-vscode/wiki/Connected-mode