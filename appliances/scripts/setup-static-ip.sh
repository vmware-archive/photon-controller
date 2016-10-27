#!/bin/bash -x

DNS=$1
ADDRESS=$2
GATEWAY=$3

rm -f /etc/systemd/network/*.network
systemctl stop systemd-networkd
eno_name=$(ip addr | grep eno | sed 's/.*\(eno.*\):.*/\1/' | head -n 1)
cat > "/etc/systemd/network/10-dhcp-${eno_name}.network" << EOF
[Match]
Name=${eno_name}

[Network]
$DNS

[Address]
Address=$ADDRESS

[Route]
Gateway=$GATEWAY
EOF
ip addr flush label "${eno_name}"
systemctl restart systemd-networkd
ip=`grep Address= /etc/systemd/network/10-dhcp-${eno_name}.network | sed 's/.*=\.*//' | sed 's/\/.*//'`
echo $ip
c_ip=`ifconfig ${eno_name} | sed -n '/dr:/{;s/.*dr://;s/ .*//;p;}'`
while [ "$ip" != "$c_ip" ]
do
  ip addr flush label "${eno_name}"
  systemctl restart systemd-networkd
  c_ip=`ifconfig ${eno_name} | sed -n '/dr:/{;s/.*dr://;s/ .*//;p;}'`
  echo $c_ip
  sleep 1
done
ping -q -c 4 $GATEWAY
