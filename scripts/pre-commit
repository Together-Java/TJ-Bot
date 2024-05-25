#!/bin/sh

create_stash() {
  # Stash unstaged changes, if any
  previous_stash=$(git rev-parse -q --verify refs/stash)
  git stash push -q
  new_stash=$(git rev-parse -q --verify refs/stash)
}

pop_stash() {
  # Restore unstaged changes, if any
  if [ "$previous_stash" != "$new_stash" ]; then
    git stash apply -q && git stash drop -q
  fi
}

echo "****Running pre-commit hooks****"
# We want to apply spotless on all staged files. Therefore, we have to first draft a
# WIP-commit for the staged changes and put the unstaged changes aside using stash
# (otherwise spotless would apply on them as well, see https://github.com/diffplug/spotless/issues/623).
# There are a few edge cases to handle here; for example if there are no unstaged changes, stash would not create a stash.
# Hence, the following pop of the stash must be guarded (see https://stackoverflow.com/a/20480591/2411243).

# Mini commit for staged changes, so that we can stash away unstaged in the next step (bypass hook with "no-verify" to avoid recursion)
git commit --no-verify --message "WIP"
commitExitCode=$?
if [ "$commitExitCode" -ne 0 ]; then
  # Commit failed (for example if there is nothing staged), early-out
  exit "$commitExitCode"
fi

# Put unstaged away
create_stash
# Get back staged changes
git reset --soft HEAD^

# Apply spotless on the staged changes only (rest has been put away)
echo "**Applying spotless**"
./gradlew spotlessApply
spotlessExitCode=$?
if [ "$spotlessExitCode" -ne 0 ]; then
  # Spotless failed, restore unstaged
  pop_stash
  exit "$spotlessExitCode"
fi

# Spotless possibly found changes, apply them, excluding untracked files
git add -u

# Restore back the unstaged changes
pop_stash
echo "****Done pre-commit hooks****"
