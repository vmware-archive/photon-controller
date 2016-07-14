#!/bin/bash
set -x -e

export BRANCH=${GERRIT_BRANCH:-`git rev-parse --abbrev-ref HEAD`}
export VERSION=${BRANCH}
packer build ./packer/docker.json
