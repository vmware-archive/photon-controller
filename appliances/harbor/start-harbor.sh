#!/bin/bash -x
# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

admin_password="$1"
eno_name=$(ip addr | grep eno | sed 's/.*\(eno.*\):.*/\1/' | head -n 1)
ipAddress=`ifconfig ${eno_name} | sed -n '/dr:/{;s/.*dr://;s/ .*//;p;}'`
sed -i s/hostname.*/"hostname = ${ipAddress}"/ /root/harbor/harbor.cfg
sed -i s/harbor_admin_password.*/"harbor_admin_password = ${admin_password}"/ /root/harbor/harbor.cfg
sed -i s/db_password.*/"db_password = ${admin_password}"/ /root/harbor/harbor.cfg
sed -i s/ui_url_protocol.*/"ui_url_protocol = https"/ /root/harbor/harbor.cfg

echo "Generating CA certificate"
openssl req -newkey rsa:4096 -nodes -sha256 \
  -keyout /root/ca.key -x509 -days 365 \
  -out /root/ca.crt \
  -subj "/C=US/ST=California/L=Palo Alto/O=VMware/OU=VMware Engineering/CN=pc-harbor.local"

echo "Generating certificate signing request"
openssl req -newkey rsa:4096 -nodes -sha256 \
  -keyout /root/pc-harbor.local.key \
  -out /root/pc-harbor.local.csr \
  -subj "/C=US/ST=California/L=Palo Alto/O=VMware/OU=VMware Engineering/CN=pc-harbor.local"

echo "Generating certificate for the Harbor registry host"
mkdir -p /root/demoCA
pushd /root/demoCA
touch index.txt
echo '01' > serial
popd

echo subjectAltName = IP:${ipAddress} > /root/extfile.cnf
pushd /root
openssl ca \
  -batch \
  -in /root/pc-harbor.local.csr \
  -out /root/pc-harbor.local.crt \
  -cert /root/ca.crt \
  -keyfile /root/ca.key \
  -extfile /root/extfile.cnf \
  -outdir /root/
popd

echo "Configuring Nginx"
pushd /root/harbor/config/nginx
mkdir -p cert/
cp /root/pc-harbor.local.crt cert/
cp /root/pc-harbor.local.key cert/
mv nginx.conf nginx.conf.bak
cp nginx.https.conf nginx.conf
sed -i "s|server_name harbordomain.com|server_name ${ipAddress}|g" nginx.conf
sed -i "s|harbordomain.crt|pc-harbor.local.crt|g" nginx.conf
sed -i "s|harbordomain.key|pc-harbor.local.key|g" nginx.conf
popd

pushd /root/harbor
echo "Preparing Harbor And Starting harbor"
./install.sh
popd
echo "Harbor is up"

echo "Exposing public CA certificate"
docker exec harbor_ui_1 mkdir -p /harbor/static/resources/certs/
docker cp /root/ca.crt harbor_ui_1:/harbor/static/resources/certs/
