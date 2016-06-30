# Building Virtual Box ready PhotonOS OVA

To create a PhotonOS ova that is compatible with virtual box you should be able to simply Requirement

    ./build.sh

The resulting ova is ``build/photon-ova-virtualbox.ova``.

If you would like to base your ova on a specific version of photon os you need to provide the url to the respective PhotonOS:

    export PHOTON_ISO_URL=http://photon.os/version.iso

## Required binaries
- We use packer to create the actual
      brew install packer
- packer requires a sha1 checksum to verify it copied the ova correctly
      brew install md5sha1sum
- packer fires up everything in a virtual machine hosted by virtual box
      brew install virtualbox
