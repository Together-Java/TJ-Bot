# TJ-Bot

[![codefactor](https://img.shields.io/codefactor/grade/github/together-java/tj-bot)](https://www.codefactor.io/repository/github/together-java/tj-bot)
![Java](https://img.shields.io/badge/Java-17%2B-ff696c)
[![license](https://img.shields.io/github/license/Together-Java/TJ-Bot)](https://github.com/Together-Java/TJ-Bot/blob/master/LICENSE)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Together-Java/TJ-Bot?label=release)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Together-Java_TJ-Bot&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=Together-Java_TJ-Bot)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Together-Java_TJ-Bot&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=Together-Java_TJ-Bot)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Together-Java_TJ-Bot&metric=security_rating)](https://sonarcloud.io/dashboard?id=Together-Java_TJ-Bot)

TJ-Bot is a Discord Bot used on the [Together Java](https://discord.com/invite/XXFUXzK) server. It is maintained by the community, anyone can contribute.

# Getting started

* [Documentation](https://github.com/Together-Java/TJ-Bot/wiki): as general entry point to the project
* [Contributing](https://github.com/Together-Java/TJ-Bot/wiki/Contributing): if you want to improve the bot

# Download

## Using a build tool

### Gradle

First add the jitpack repository:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
} 
```
Then add the dependency:
```gradle
dependencies {
    implementation 'com.github.Together-Java:TJ-Bot:<tag>'
}
```
You can replace `<tag>` by either a commit hash (the first 10 characters) or by `-SNAPSHOT` to get the latest version.

### Maven

First add the jitpack repository:
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
<repository>
```
Then add the dependency:
```xml
<dependency>
    <groupId>com.github.Together-Java</groupId>
    <artifactId>TJ-Bot</artifactId>
    <version>[tag]</version>
</dependency>
```
You can replace `[tag]` by either a commit hash (the first 10 characters) or by `-SNAPSHOT` to get the latest version.

---

## Jar downloads

Jar downloads are available from the [release section](https://github.com/Together-Java/TJ-Bot/releases).
