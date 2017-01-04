#!/bin/bash -e
# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

# The UI container is not available to the outside world, thus making the UI
# install optional.
if [ -z "$MGMT_UI_CONTAINER_URL" ]; then
  echo "Skipping installing management ui."
else
  echo "Installing the management ui."
  export MGMT_UI_TAR=`basename ${MGMT_UI_CONTAINER_URL}`

  wget -q ${MGMT_UI_CONTAINER_URL}

  systemctl start docker
  docker load -i ${MGMT_UI_TAR}
  docker tag esxcloud/management_ui vmware/photon-controller-ui
  docker rmi esxcloud/management_ui

  # remove container tar, no need to keep it around
  rm -rf ${MGMT_UI_TAR}
fi
