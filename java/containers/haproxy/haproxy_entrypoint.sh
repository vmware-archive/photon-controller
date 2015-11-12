#!/bin/bash

#
# This file sets default parameters and starts haproxy load balancer
#

HAPROXY="/etc/haproxy"
OVERRIDE="/haproxy-override"
PIDFILE="/var/run/haproxy.pid"

CONFIG="haproxy.cfg"
CONFIG_FILE_WITH_PATH="$HAPROXY/$CONFIG"
ERRORS="errors"

loadbalancer_http_port=$ESXCLOUD_CONFIG_loadbalancer_http_port
loadbalancer_https_port=$ESXCLOUD_CONFIG_loadbalancer_https_port
server_names=$ESXCLOUD_CONFIG_loadbalancer_servernames
server_ips=$ESXCLOUD_CONFIG_server_ips
service_port=$ESXCLOUD_CONFIG_service_port
en_name=$(ip addr show label "en*" | head -n 1 | sed 's/^[0-9]*: \(en.*\): .*/\1/')
container_ip=$(ifconfig "$en_name" | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')

if [ -z "$loadbalancer_http_port" ]
then
  echo "Invalid null or empty parameter ESXCLOUD_CONFIG_loadbalancer_http_port"
  exit -1
fi

if [ -z "$loadbalancer_https_port" ]
then
  echo "Invalid null or empty parameter ESXCLOUD_CONFIG_loadbalancer_https_port"
  exit -1
fi

if [ -z "$server_names" ]
then
  echo "Invalid null or empty parameter ESXCLOUD_CONFIG_server_names"
  exit -1
fi

if [ -z "$server_ips" ]
then
  echo "Invalid null or empty parameter ESXCLOUD_CONFIG_server_ips"
  exit -1
fi

if [ -z "$service_port" ]
then
  echo "Invalid null or empty parameter ESXCLOUD_CONFIG_service_port"
  exit -1
fi

if [ -z "$container_ip" ]
then
  echo "Invalid container IP address retrieved."
  exit -1
fi

IFS=","
server_names_array=($server_names)
server_ips_array=($server_ips)

if [ 0 -eq ${#server_names_array[@]} ] || [ 0 -eq ${#server_ips_array[@]} ]
then
  echo "Invalid empty array of elements for server names or server ips"
  exit -1
fi

if [ ${#server_names_array[@]} -ne ${#server_ips_array[@]} ]
then
  echo "Mismatch number of server names vs. server ips. ${#server_names_array[@]} vs ${#server_ips_array[@]}"
  exit -1
fi

cd "$HAPROXY"

# Symlink errors directory
if [[ -d "$OVERRIDE/$ERRORS" ]]; then
  mkdir -p "$OVERRIDE/$ERRORS"
  rm -fr "$ERRORS"
  ln -s "$OVERRIDE/$ERRORS" "$ERRORS"
fi

# Symlink config file.
if [[ -f "$OVERRIDE/$CONFIG" ]]; then
  rm -f "$CONFIG"
  ln -s "$OVERRIDE/$CONFIG" "$CONFIG"
fi

if [ ! -f /etc/ssl/private/esxcloud.pem ]
then
  mkdir -p /etc/ssl/private
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/esxcloud.key -out /tmp/esxcloud.crt -subj "/C=US/ST=WA/L=Bellevue/O=Vmware/OU=Esxcloud/CN=esxcloud.vmware.com"
  cat /tmp/esxcloud.crt /tmp/esxcloud.key > /etc/ssl/private/esxcloud.pem
fi

sed -i "s/  log .* local0/  log ${container_ip} local0/g" $CONFIG_FILE_WITH_PATH
sed -i "s/  bind http 0.*/  bind ${container_ip}:${loadbalancer_http_port}/g" $CONFIG_FILE_WITH_PATH
sed -i "s/  bind https 0.*/  bind ${container_ip}:${loadbalancer_https_port} ssl crt \/etc\/ssl\/private\/esxcloud.pem/g" $CONFIG_FILE_WITH_PATH

for (( i=0; i<${#server_ips_array[@]}; i++ )) ;
do
  echo "  server ${server_names_array[$i]} ${server_ips_array[$i]} check" >> $CONFIG_FILE_WITH_PATH
done

echo "haproxy config file:"
cat $CONFIG_FILE_WITH_PATH
exec haproxy -d -f /etc/haproxy/haproxy.cfg -p "$PIDFILE"
