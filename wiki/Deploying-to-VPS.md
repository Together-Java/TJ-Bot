# Overview

This document explains how deployments to our VPS infrastructure work across all repositories. It is intended to help contributors understand our branching model, CI/CD pipeline, and repository setup process.

# Branches

Most of our repositories use two primary branches:

1. develop
2. master

## `develop` branch
The develop branch contains ongoing development work and changes that are not yet released to production.

In some repositories, this branch may automatically deploy to a testing or staging environment. 
For example in TJ-Bot, changes pushed to develop are deployed to a testing server.

This branch should be considered unstable and under active development.

## `master` branch

The `master` branch contains production-ready code that is currently deployed to live environments.

When changes are ready for release:

* `master` is rebased from `develop`
* Production deployment is triggered after the update

# CI/CD Workflow
We use [Woodpecker CI](https://woodpecker-ci.org/) on our VPS as our continuous integration and deployment engine.

Woodpecker is configured to trigger when changes are merged into either:

* `develop`
* `master`

The behavior depends on repository-specific configuration located in `.woodpecker.yml` which is stored in the root of each repository.

## Deployment Responsibilities

By design, our Woodpecker pipelines are intentionally minimal and only handle deployment tasks. Typically, pipelines:

1. Build container images using Gradle’s jib task
2. Push images to our container registry
3. Notify Watchtower to restart containers with updated images

## Why We Use Woodpecker CI

We allow multiple GitHub teams to have write access to our repositories. This improves contributor experience by allowing pull requests without requiring repository forks. 

However, this model introduces security risks.

### Security Concerns with GitHub Actions

If GitHub Actions were used for deployments:

* Any contributor with write access could potentially create or modify workflows
* A malicious or compromised account could expose repository secrets (e.g. registry credentials or Watchtower tokens)
* This could allow attackers to:
  * Push malicious container images 
  * Execute remote code 
  * Compromise infrastructure

While we trust our contributors, we design our systems with compromise scenarios in mind.

### Benefits of Woodpecker

Woodpecker CI was chosen because:

* Secrets are stored and executed outside GitHub
* It reduces attack surface from pull request workflows
* Migration from GitHub Actions was straightforward
* The project is actively maintained and responsive to security issues
* It requires minimal operational overhead

# Setting Up CI/CD for a New Repository

Follow these steps to enable deployments for a repository.

## 1. Enable the Repository in Woodpecker

Requirements:

* You must be an organization administrator (Moderator)

Steps:

* Visit: https://woodpecker.togetherjava.org/repos
* Click "Add repository"
* Locate the repository
* Click "Enable"

## 2. Configure Repository Secrets

After enabling the repository, configure required secrets such as:

* Watchtower token
* Docker registry username
* Docker registry password
* Any repository-specific credentials

Secrets are managed inside Woodpecker, not inside GitHub.

## 3. Create a Woodpecker Pipeline

Create a `.woodpecker.yml` file in the repository root directory.

This file defines:

* Build steps
* Image publishing
* Deployment triggers
* Environment-specific behaviour

Pipeline configuration may vary depending on repository requirements.
