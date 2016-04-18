#!/bin/bash -xe

# setting up photon user
groupadd photon
useradd -g photon photon
echo photon:vmware | chpasswd
mkdir /home/photon
chown photon:photon /home/photon

# creating the default networking configuration
# echo '[Match]
# Name=enp0s3
#
# [Network]
# DHCP=yes' > /etc/systemd/network/10-dhcp-enp0s3.network
#
# echo '[Match]
# Name=enp0s8
#
# [Network]
# Address=172.31.253.37/24' > /etc/systemd/network/10-dhcp-enp0s8.network

#rm -f /etc/systemd/network/10-dhcp-en*.network
ip addr flush label "enp0s8"
systemctl restart systemd-networkd
sleep 30

# setting up ssh config
if [ ! -z "$(grep PermitRootLogin /etc/ssh/sshd_config)" ] ; then sed -i 's/.*PermitRootLogin.*/PermitRootLogin no/g' /etc/ssh/sshd_config ; else echo "PermitRootLogin no" >> /etc/ssh/sshd_config ; fi
if [ ! -z "$(grep UseDNS /etc/ssh/sshd_config)" ] ; then sed -i 's/.*UseDNS.*/UseDNS no/g' /etc/ssh/sshd_config ; else echo "UseDNS no" >> /etc/ssh/sshd_config ; fi
systemctl restart sshd

# settig up docker
sed -i 's/ExecStart.*/ExecStart=\/bin\/docker -d -s overlay -H tcp:\/\/0.0.0.0:2375 -H unix:\/\/\/var\/run\/docker.sock/g' /lib/systemd/system/docker.service
systemctl daemon-reload
systemctl enable docker
systemctl start docker
sleep 10
