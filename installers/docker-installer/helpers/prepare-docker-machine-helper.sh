#!/bin/sh +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

ROOT=${1:-\/}

if [ -n "$GERRIT_REFSPEC" ]; then
  # This a CI run creating systemd directories for the Lightwave container needs to be run as root.
  sudo mkdir -p ${ROOT}sys/fs/cgroup/systemd
  sudo mount -t cgroup -o none,name=systemd cgroup ${ROOT}sys/fs/cgroup/systemd || true
  sudo mkdir -p ${ROOT}sys/fs/cgroup/systemd/user
  echo $$ | sudo tee -a ${ROOT}sys/fs/cgroup/systemd/user/cgroup.procs
else
  # boot2docker VMs do not have this directory present. Create it for systemd in Lightwave container.
  mkdir -p ${ROOT}sys/fs/cgroup/systemd
  mount -t cgroup -o none,name=systemd cgroup ${ROOT}sys/fs/cgroup/systemd || true
  mkdir -p ${ROOT}sys/fs/cgroup/systemd/user
  echo $$ | tee -a ${ROOT}sys/fs/cgroup/systemd/user/cgroup.procs
fi
