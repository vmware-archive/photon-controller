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

if [ "$1" = "--force" ] || [ "$1" = "-f" ]; then
    DEVBOX_FORCE_PHOTON_PLUGIN_REINSTALL=true
    shift
fi

if [ ! -z "$1" ]; then
    echo "Usage: install_photon_plugin [--force | -f]"
    exit
fi

if [ -z "$(vagrant plugin list | grep vagrant-guests-photon)" ]; then
  echo "Installing Photon plugin for Vagrant."
  vagrant plugin install vagrant-guests-photon
elif [ ! -z "$DEVBOX_FORCE_PHOTON_PLUGIN_REINSTALL" ]; then
  echo "Forcing photon plugin reinstall"
  # Note that it's safe to uninstall even if it's not installed
  # Vagrant is polite about such requests.
  vagrant plugin uninstall vagrant-guests-photon
  vagrant plugin install vagrant-guests-photon
else
  echo "Photon plugin for Vagrant is already installed."
fi
