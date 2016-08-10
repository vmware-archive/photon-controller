#!/bin/bash -xe

echo "installing photon-controller"

# install photon-controller
tdnf install -y openjdk
mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default

rpm -i /tmp/photon-controller*.rpm

# install lightwave client
echo "installing lightwave client"
mkdir -p /var/run/sshd; chmod -rx /var/run/sshd
rm -rf /etc/ssh/ssh_host_rsa_key
ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key

# configure journald
sed -i 's/#Storage=auto/Storage=persistent/' /etc/systemd/journald.conf

# install lightwave
tdnf install -y procps-ng
tdnf install -y likewise-open
tdnf install -y boost
tdnf install -y vmware-lightwave-clients

# open iptables ports
# open lightwave client ports
echo "iptables -I INPUT -p tcp --dport 22 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p udp --dport 53 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 53 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p udp --dport 88 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 88 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 389 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 443 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 636 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 2012 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 2014 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 2020 -j ACCEPT" >> /etc/systemd/scripts/iptables
# open photon-controller ports
echo "iptables -I INPUT -p tcp --dport 9000 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 19000 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 20001 -j ACCEPT" >> /etc/systemd/scripts/iptables
