#!/bin/sh +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

ROOT=${1:-\/}

# boot2docker VMs do not have this directory present. Create it for systemd in lightwave container.
mkdir -p ${ROOT}sys/fs/cgroup/systemd
mount -t cgroup -o none,name=systemd cgroup ${ROOT}sys/fs/cgroup/systemd || true
mkdir -p ${ROOT}sys/fs/cgroup/systemd/user
echo $$ | tee -a ${ROOT}sys/fs/cgroup/systemd/user/cgroup.procs
