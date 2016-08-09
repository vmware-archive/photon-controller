#!/bin/bash -xe

echo "installing lightwave"

mkdir -p /var/run/sshd; chmod -rx /var/run/sshd
rm -rf /etc/ssh/ssh_host_rsa_key
ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key

# configure journald
sed -i 's/#Storage=auto/Storage=persistent/' /etc/systemd/journald.conf
tdnf install -y procps-ng
tdnf install -y commons-daemon apache-tomcat boost-1.56.0
tdnf install -y likewise-open-6.2.9
tdnf install -y vmware-lightwave-server

# open iptables ports
# EXPOSE 22 53/udp 53 88/udp 88 389 443 636 2012 2014 2020
iptables -I INPUT -p tcp --dport 22 -j ACCEPT
iptables -I INPUT -p udp --dport 53 -j ACCEPT
iptables -I INPUT -p tcp --dport 53 -j ACCEPT
iptables -I INPUT -p udp --dport 88 -j ACCEPT
iptables -I INPUT -p tcp --dport 88 -j ACCEPT
iptables -I INPUT -p tcp --dport 389 -j ACCEPT
iptables -I INPUT -p tcp --dport 443 -j ACCEPT
iptables -I INPUT -p tcp --dport 636 -j ACCEPT
iptables -I INPUT -p tcp --dport 2012 -j ACCEPT
iptables -I INPUT -p tcp --dport 2014 -j ACCEPT
iptables -I INPUT -p tcp --dport 2020 -j ACCEPT
