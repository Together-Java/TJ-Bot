# Overview

Thanks to a rich pipeline, releasing a new version of the bot is fairly simple.

It mainly consists of simply **pushing** `develop` over on `master`, creating an **annotated tag** for the release and possibly adjusting the **configuration** and the Discord environment, thats it.

## Checklist

1. Determine the next release version (for example `v1.2.3`)
2. Create a PR to merge `develop` into `master`, call it for example `Release v1.2.3` and tag it as `release`; the PRs only purpose is visibility
3. Ignore the PR(don't merge it via Github) and `rebase` `master` directly onto `develop`, then `force-push`(might not need to do this, try just pushing). As a result, `master` and `develop` are fully identical  
  3.1. The PR should now automatically be marked as _merged_ by GitHub  
  3.2. In the meantime, the pipeline automatically started deploying the new version to the server
  
   *Note: for those who are not good with rebase, make sure to have your `develop` branch upto date. Switch to `master`, do `git rebase develop`.*
4. Create and push an **annotated tag** like `v.1.2.3` with a short release description from the state of `master`  
  4.1. The pipeline will now create a new release on GitHub  
  4.2. Once the release has been created, you can adjust and beautify the description, see [releases](https://github.com/Together-Java/TJ-Bot/releases)
  Note: There's two types of tags, annotated and normal tags. We want annotated tags, to create one via intellij follow instructions in given screenshot
 
   CREATING AN ANNOTATED TAG IN INTELLIJ

   ![image](https://github.com/Together-Java/TJ-Bot/assets/61616007/fcfeec1e-7f72-4d8a-80af-92eed7839d3f)

   PUSHING ANNOTATED TAG

   `git push --follow-tags`

   read more here: https://git-scm.com/docs/git-push

5. In case the configuration (`config.json`) changed, make sure to update it; see [[Edit the Config]]
6. In case the new version requires changes on Discord, such as other permissions, new channels or roles, make sure to update them as well
7. Verify that the bot works as expected  
  7.1. Try `/ping` and see if you get a response  
  7.2. Maybe check the logs to see if any error pops up, see [[View the logs]]