#!/bin/bash -xe

# adding vms ip address to the configuration files
HOST_IP=`ifconfig | grep -a1 enp0s3 | grep inet | awk '{print $2}' | awk -F\: '{print $2}'`
## modify dhcp config
## modify apache config
## modify boot.cfg
sed -i "s/prefix=http:\/\/.*/prefix=http:\/\/${HOST_IP}\/installer-pxe-modules\/g" /etc/bmp/bootcfg
## modify ipxe.tmpl
sed -i "s/kernel\ -n\ mboot.c32.*/kernel -n mboot.c32 http:\/\/${HOST_IP}\/installer-pxe-modules\/mboot.c32/g"

# running the dhcp container
docker run --restart=always \
-p 53:53/tcp -p 53:53/udp -p 68:68/udp -p 67:67/udp -p 69:69/udp \
--net=host --cap-add=NET_ADMIN \
-v /etc/bmp:/etc/bmp \
--entrypoint /bin/sh andyshinn/dnsmasq \
-c '/usr/sbin/dnsmasq -k -h --conf-file=/etc/bmp/dnsmasq.conf'

# running the apache container
docker run --restart=always \
-p 80:80 \
-v /etc/bmp:/etc/bmp \
--entrypoint /bin/sh httpd \
-c 'httpd-foreground -f /etc/bmp/httpd.conf'
