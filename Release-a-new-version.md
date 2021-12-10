# Overview

Thanks to a rich pipeline, releasing a new version of the bot is fairly simple.

It mainly consists of simply **pushing** `develop` over on `master`, creating an **annotated tag** for the release and possibly adjusting the **configuration** and the Discord environment, thats it.

## Checklist

1. Determine the next release version (for example `v1.2.3`)
2. Create a PR to merge `develop` into `master`, call it for example `Release v1.2.3` and tag it as `release`; the PRs only purpose is visibility
3. Ignore the PR and `rebase` `master` directly onto `develop`, then `force-push`. As a result, `master` and `develop` are fully identical  
  3.1. The PR should now automatically be marked as _merged_ by GitHub  
  3.2. In the meantime, the pipeline automatically started deploying the new version to the server  
4. Create and push an **annotated tag** like `v1.2.3` with a short release description from the state of `master`  
  4.1. The pipeline will now create a new release on GitHub  
  4.2. Once the release has been created, you can adjust and beautify the description, see [releases](https://github.com/Together-Java/TJ-Bot/releases)
5. In case the configuration (`config.json`) changed, make sure to update it; see [[Edit the Config]]
6. In case the new version requires changes on Discord, such as other permissions, new channels or roles, make sure to update them as well
7. Verify that the bot works as expected  
  7.1. Try `/ping` and see if you get a response  
  7.2. Maybe chech the logs to see if any error pops up, see [[View the logs]]