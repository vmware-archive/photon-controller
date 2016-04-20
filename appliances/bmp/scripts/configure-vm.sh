#!/bin/bash -xe

# setting up photon user
groupadd photon
useradd -g photon photon
echo photon:vmware | chpasswd
mkdir /home/photon
chown photon:photon /home/photon

ip addr flush label "enp0s8"
systemctl restart systemd-networkd
sleep 30

# setting up ssh config
if [ ! -z "$(grep PermitRootLogin /etc/ssh/sshd_config)" ] ; then sed -i 's/.*PermitRootLogin.*/PermitRootLogin no/g' /etc/ssh/sshd_config ; else echo "PermitRootLogin no" >> /etc/ssh/sshd_config ; fi
if [ ! -z "$(grep UseDNS /etc/ssh/sshd_config)" ] ; then sed -i 's/.*UseDNS.*/UseDNS no/g' /etc/ssh/sshd_config ; else echo "UseDNS no" >> /etc/ssh/sshd_config ; fi
systemctl restart sshd

systemctl daemon-reload
systemctl enable dnsmasq.service
systemctl start dnsmasq.service

sleep 5
