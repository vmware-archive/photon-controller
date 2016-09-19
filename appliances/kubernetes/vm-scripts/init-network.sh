#!/bin/bash -x

eno_name=$(ip addr | grep eno | sed 's/.*\(eno.*\):.*/\1/' | head -n 1)
cat > "/etc/systemd/network/10-dhcp-${eno_name}.network" << EOF
[Match]
Name=${eno_name}

[Network]
DHCP=yes
EOF
ip addr flush label "${eno_name}"
systemctl restart systemd-networkd
timeout=20
second=0
DEFAULT_INTERFACE=$(ip -o -4 route show to default | awk '{print $5}' | head -1)
# waiting for the interface to be not loopback
echo "current interface: "
echo $DEFAULT_INTERFACE
ip addr
while [[ "${DEFAULT_INTERFACE}" == "" ]] || [[ "${DEFAULT_INTERFACE}" == "lo" ]]; do
  ((second++))
  echo ${second}
    if [[ ${second} -gt ${timeout} ]]; then
      echo "ERROR: No network interface ready!"
      break
    fi
    sleep 1
    DEFAULT_INTERFACE=$(ip -o -4 route show to default | awk '{print $5}' | head -1)
done
echo "done: Output IP ADDR for ip addr is "
ip addr
