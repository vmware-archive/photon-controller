#!/bin/bash -xe

# disable root login
if [ ! -z "$(cat /etc/ssh/sshd_config | grep PermitRootLogin)" ] ; then
  sed -i 's/.*PermitRootLogin.*/PermitRootLogin no/g' /etc/ssh/sshd_config
else
  echo "PermitRootLogin no" >> /etc/ssh/sshd_config
fi

# create photon user
useradd photon
echo photon:changeme | chpasswd
mkdir -p /home/photon
chown photon:users /home/photon
