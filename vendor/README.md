# Xenon framework update

This document contains instructions on how to pick up a new version of the Xenon framework.

## 1. Update the submodule pointer

- Go to the [Xenon GitHub page](https://github.com/vmware/xenon/commits/master)
- Find the hash of the commit to be picked up
- `cd vendor/xenon`
- `git pull origin %commit_hash%`

## 2. Update the jar version in java/build.gradle

- Go to the [DCP build job](https://enatai-jenkins.eng.vmware.com/job/dcp/) on the Enatai Jenkins server.
- Find the "promote" build (it has a star next to it) that promoted the commit_hash from above.
- Open the promoted log (this is not the console log -- it is the log link next to "Successfully promoted").
- Determine the full file name for "dcp-host-*-jar-with-dependencies" file.
- Update the version of the dcp-host artifact specified in build.gradle to the value matched by the '*' above.

### 3. Verify the update and commit

- Build the entire "java" folder and run unit tests.
- Fix all build breaks and unit test failures.
- Commit all changed files, including the submodule pointer and build.gradle file.
