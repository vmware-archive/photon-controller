#!/bin/bash
# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

HAPROXY="/etc/haproxy"
OVERRIDE="/haproxy-override"
PIDFILE="/var/run/haproxy.pid"

CONFIG="haproxy.cfg"
CONFIG_FILE_WITH_PATH="$HAPROXY/$CONFIG"
ERRORS="errors"

cd "$HAPROXY"

# Symlink errors directory
if [[ -d "$OVERRIDE/$ERRORS" ]]; then
  mkdir -p "$OVERRIDE/$ERRORS"
  rm -fr "$ERRORS"
  ln -s "$OVERRIDE/$ERRORS" "$ERRORS"
fi

if [ ! -f /etc/ssl/private/photon_haproxy.pem ]
then
  if [ -n "$HAPROXY_IP" ]
  then
    container_ip="$HAPROXY_IP"
  else
    en_name=$(ip addr show label "en*" | head -n 1 | sed 's/^[0-9]*: \(en.*\): .*/\1/')
    container_ip=$(ifconfig $en_name | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')
  fi
  mkdir -p /etc/ssl/private
  cp /etc/ssl/openssl.cnf /tmp/
  sed -i 's/\[ v3_req \]/\[ v3_req \]\nsubjectAltName = @alt_names\n/' /tmp/openssl.cnf
  sed -i 's/basicConstraints = CA:FALSE/basicConstraints = critical,CA:true\n/g' /tmp/openssl.cnf
  sed -i 's/basicConstraints=CA:FALSE/basicConstraints = critical,CA:true\n/g' /tmp/openssl.cnf
  sed -i 's/keyUsage = nonRepudiation, digitalSignature, keyEncipherment/keyUsage = nonRepudiation, digitalSignature, keyEncipherment, keyCertSign, cRLSign/g' /tmp/openssl.cnf
  printf "\n[ alt_names ]\nIP.1 = %s\n" $container_ip >> /tmp/openssl.cnf
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/photon_haproxy.key -out /tmp/photon_haproxy.crt -subj "/C=US/ST=WA/L=Bellevue/O=Vmware/OU=Photon/CN=$container_ip" -config /tmp/openssl.cnf -extensions v3_req
  cat /tmp/photon_haproxy.crt /tmp/photon_haproxy.key > /etc/ssl/private/photon_haproxy.pem
fi

# Symlink config file.
if [[ -f "$OVERRIDE/$CONFIG" ]]; then
  rm -f "$CONFIG"
  ln -s "$OVERRIDE/$CONFIG" "$CONFIG"
fi

echo "haproxy config file:"
cat $CONFIG_FILE_WITH_PATH
exec haproxy -d -f /etc/haproxy/haproxy.cfg -p "$PIDFILE"
