#!/usr/bin/env bash

DIR="$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
FILENAME="dhcpServerTestConfig"

while IFS='' read -r line || [[ -n "$line" ]]; do
    if [[ $line == *"error"* ]]
    then
      (>&1 echo "● dnsmasq.service - A lightweight DHCP and caching DNS server
   Loaded: loaded (/usr/lib/systemd/system/dnsmasq.service; enabled; vendor preset: enabled)
   Active: failed (Result: exit-code) since mar 2015-12-08 17:18:16 WET; 55s ago
     Docs: man:dnsmasq(8)
  Process: 3282 ExecStart=/usr/bin/dnsmasq -k --enable-dbus --user=dnsmasq --pid-file (code=exited, status=2)
  Process: 3280 ExecStartPre=/usr/bin/dnsmasq --test (code=exited, status=0/SUCCESS)
 Main PID: 3282 (code=exited, status=2)

dic 08 17:18:16 Server systemd[1]: Starting A lightweight DHCP and caching DNS server...
dic 08 17:18:16 Server dnsmasq[3280]: dnsmasq: syntax check OK.
dic 08 17:18:16 Server dnsmasq[3282]: dnsmasq: failed to create listening socket for port 53: Address already in use
dic 08 17:18:16 Server systemd[1]: dnsmasq.service: Main process exited, code=exited, status=2/INVALIDARGUMENT
dic 08 17:18:16 Server systemd[1]: Failed to start A lightweight DHCP and caching DNS server.
dic 08 17:18:16 Server systemd[1]: dnsmasq.service: Unit entered failed state.
dic 08 17:18:16 Server systemd[1]: dnsmasq.service: Failed with result 'exit-code'.")
      exit 0
    else
      (>&1 echo "● dnsmasq.service - dnsmasq - A lightweight DHCP and caching DNS server
   Loaded: loaded (/lib/systemd/system/dnsmasq.service; enabled)
  Drop-In: /run/systemd/generator/dnsmasq.service.d
           └─50-dnsmasq-$named.conf, 50-insserv.conf-$named.conf
   Active: active (running) since Mon 2016-05-02 08:11:52 UTC; 17h ago
 Main PID: 2734 (dnsmasq)
   CGroup: /system.slice/dnsmasq.service
           └─2734 /usr/sbin/dnsmasq -x /var/run/dnsmasq/dnsmasq.pid -u dnsmasq -r /var/run/dnsmasq/resolv.conf -7 /etc/dnsmasq.d,.dpkg-dist,.dpkg-old,.dpkg-new --local-service --trust-anchor=.,19036,8,2,49AAC11D7B6F6446702E54A1607371607A1A41855200FD2CE1CDDE32F24E8FB5

May 02 08:11:52 raspberrypi dnsmasq[2724]: dnsmasq: syntax check OK.
May 02 08:11:52 raspberrypi dnsmasq[2734]: started, version 2.72 cachesize 150
May 02 08:11:52 raspberrypi dnsmasq[2734]: compile time options: IPv6 GNU-getopt DBus i18n IDN DHCP DHCPv6 no-Lua TFTP conntrack ipset auth DNSSEC loop-detect
May 02 08:11:52 raspberrypi dnsmasq[2734]: DNS service limited to local subnets
May 02 08:11:52 raspberrypi dnsmasq[2734]: reading /var/run/dnsmasq/resolv.conf
May 02 08:11:52 raspberrypi dnsmasq[2734]: using nameserver 192.168.1.2#53
May 02 08:11:52 raspberrypi dnsmasq[2734]: read /etc/hosts - 5 addresses
May 02 08:11:52 raspberrypi systemd[1]: Started dnsmasq - A lightweight DHCP and caching DNS server.")
    fi
done < "$DIR/$FILENAME"

exit 0