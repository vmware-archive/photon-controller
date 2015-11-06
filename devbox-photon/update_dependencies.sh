#!/bin/bash

if [ -z "$(vagrant plugin list | grep vagrant-guests-photon)" ]
then
  echo "Installing Photon plugin for Vagrant."
  wget --no-proxy http://10.146.64.234:8081/artifactory/vagrant-plugins/vagrant-guests-photon-0.0.1.gem
  vagrant plugin install vagrant-guests-photon-0.0.1.gem
  rm vagrant-guests-photon-0.0.1.gem
elif [ ! -z "$DEVBOX_FORCE_PHOTON_PLUGIN_REINSTALL" ]
then
  echo "Forcing photon plugin reinstall"
  vagrant plugin uninstall vagrant-guests-photon
  wget --no-proxy http://10.146.64.234:8081/artifactory/vagrant-plugins/vagrant-guests-photon-0.0.1.gem
  vagrant plugin install vagrant-guests-photon-0.0.1.gem
  rm vagrant-guests-photon-0.0.1.gem
else
  echo "Photon plugin for Vagrant is already installed."
fi
