#!/bin/bash

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

photon_vagrant_plugin_url="https://bintray.com/artifact/download/photon-controller/public/27/resource/vagrant-guests-photon-0.0.1.gem"
if [ -n "$DEVBOX_VAGRANT_PLUGIN_URL" ]
then
  photon_vagrant_plugin_url="$DEVBOX_VAGRANT_PLUGIN_URL"
fi

if [ -z "$(vagrant plugin list | grep vagrant-guests-photon)" ]
then
  echo "Installing Photon plugin for Vagrant."
  wget $photon_vagrant_plugin_url
  vagrant plugin install vagrant-guests-photon-0.0.1.gem
  rm vagrant-guests-photon-0.0.1.gem
elif [ ! -z "$DEVBOX_FORCE_PHOTON_PLUGIN_REINSTALL" ]
then
  echo "Forcing photon plugin reinstall"
  vagrant plugin uninstall vagrant-guests-photon
  wget $photon_vagrant_plugin_url
  vagrant plugin install vagrant-guests-photon-0.0.1.gem
  rm vagrant-guests-photon-0.0.1.gem
else
  echo "Photon plugin for Vagrant is already installed."
fi
