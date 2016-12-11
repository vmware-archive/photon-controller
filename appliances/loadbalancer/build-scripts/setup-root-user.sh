#!/bin/bash -xe
# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

# When logging in via ssh, root may only use ssh key, not password
# Note, because of this change, this must be the last script run as part of
# the packer build: it uses ssh to run scripts, so this will prevent future
# scripts from running.
sed -i -e 's/^PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config

# We don't chage because it makes it really hard to fetch log files during testing.
# We aren't concerned because we disable the use of passwords with ssh, so
# passwords can only be used if a user has access to the console
# chage -d 0 root

mkdir -p /home/photon
useradd photon
chown photon:users /home/photon
