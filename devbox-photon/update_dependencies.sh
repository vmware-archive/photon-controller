#!/bin/bash

if [ -z "$(vagrant plugin list | grep vagrant-guests-photon)" ]
then
  echo "Installing Photon plugin for Vagrant."
  wget https://bintray.com/artifact/download/photon-controller/public/27/resource/vagrant-guests-photon-0.0.1.gem
  vagrant plugin install vagrant-guests-photon-0.0.1.gem
  rm vagrant-guests-photon-0.0.1.gem
elif [ ! -z "$DEVBOX_FORCE_PHOTON_PLUGIN_REINSTALL" ]
then
  echo "Forcing photon plugin reinstall"
  vagrant plugin uninstall vagrant-guests-photon
  wget https://bintray.com/artifact/download/photon-controller/public/27/resource/vagrant-guests-photon-0.0.1.gem
  vagrant plugin install vagrant-guests-photon-0.0.1.gem
  rm vagrant-guests-photon-0.0.1.gem
else
  echo "Photon plugin for Vagrant is already installed."
fi
