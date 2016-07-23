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



# Accept optional parameters for path of configuration and installation files
# Example usages: ./run.sh
#                 ./run.sh /opt/vmware/photon-controller/configuration
#                 ./run.sh /opt/vmware/photon-controller/configuration /opt/vmware/photon-controller/lib

CONFIG_PATH_PARAM=$1
INSTALLATION_PATH_PARAM=$2

CONFIG_PATH=${CONFIG_PATH_PARAM:-"/etc/esxcloud"}
API_BITS=${INSTALLATION_PATH_PARAM:-"/usr/lib/esxcloud/photon-controller-core"}
API_BIN="$API_BITS/bin"
API_LIB="$API_BITS/lib"
API_SWAGGER_JS_FILE="swagger-config.js"
API_SWAGGER_JS="$CONFIG_PATH/$API_SWAGGER_JS_FILE"
PHOTON_CONTROLLER_CORE_BIN="{{{PHOTON-CONTROLLER-CORE_INSTALL_DIRECTORY}}}/bin"
PHOTON_CONTROLLER_CORE_CONFIG="$CONFIG_PATH/photon-controller-core.yml"
SCRIPT_LOG_DIRECTORY="{{{LOG_DIRECTORY}}}/script_logs"

en_name=$(ip addr show label "en*" | head -n 1 | sed 's/^[0-9]*: \(en.*\): .*/\1/')
container_ip=$(ifconfig $en_name | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')

# Add java home and other required binaries to path. We need this here even though we are doing this during container
# build because systemd service does not seem to honor the environment variables set at container build time.
export JAVA_HOME="/usr/java/default"
export PATH=$PATH:$JAVA_HOME/bin:/opt/esxcli:/opt/vmware/bin:/opt/likewise/bin

# Create script log directory
mkdir -p $SCRIPT_LOG_DIRECTORY

#
# Add hosts entry to allow InetAddress.getLocalHost() to
# succeed when running container with --net=host
#
myhostname="$(hostname)"
if [ -z "$(grep $myhostname /etc/hosts)" ]
then
  echo "$container_ip     $myhostname" >> /etc/hosts
fi

# jvm heap size will be set to by default is 1024m
jvm_mem=1024

{{#memoryMb}}
jvm_mem=$(({{{memoryMb}}}/2))
{{/memoryMb}}

export JAVA_OPTS="-Xmx${jvm_mem}m -Xms${jvm_mem}m -XX:+UseConcMarkSweepGC {{{JAVA_DEBUG}}}"

if [ -n "$ENABLE_AUTH" -a "$ENABLE_AUTH" == "true" ]
then
  printf "window.swaggerConfig = {\n  enableAuth: true,\n  swaggerLoginUrl: '%s',\n  swaggerLogoutUrl: '%s',\n};\n" \
    $SWAGGER_LOGIN_URL $SWAGGER_LOGOUT_URL > $API_SWAGGER_JS
fi

#
# Add parameters-modified swagger-config.js to the jar
#
mkdir -p $CONFIG_PATH/assets
cp $API_SWAGGER_JS $CONFIG_PATH/assets
$JAVA_HOME/bin/jar uf ${API_LIB}/swagger-ui*.jar -C $CONFIG_PATH assets/$API_SWAGGER_JS_FILE

{{#ENABLE_AUTH}}
full_hostname="$(hostname -f)"

# Check if lightwave server is up
attempts=1
reachable="false"
total_attempts=50
while [ $attempts -lt $total_attempts ] && [ $reachable != "true" ]; do
  http_code=$(curl -w "%{http_code}" -s -X GET --insecure https://{{{LIGHTWAVE_HOSTNAME}}})
  # The curl returns 000 when it fails to connect to the lightwave server
  if [ $http_code -eq 000 ]; then
    echo "Lightwave REST server not reachable (attempt $attempts/$total_attempts), will try again."
    attempts=$[$attempts+1]
    sleep 5
  else
    reachable="true"
    break
  fi
done
if [ $attempts -eq $total_attempts ]; then
  echo "Could not connect to Lightwave REST client after $total_attempts attempts"
  exit 1
fi

# Join lightwave domain
domainjoin join {{{LIGHTWAVE_DOMAIN}}} --password {{{LIGHTWAVE_PASSWORD}}}

# Fill in the hostname and ip address for generating a machine certificate
sed -i s/IPAddress.*/"IPAddress = {{{REGISTRATION_ADDRESS}}}"/ /opt/vmware/share/config/certool.cfg
sed -i s/Hostname.*/"Hostname = $full_hostname"/ /opt/vmware/share/config/certool.cfg

mkdir -p /etc/keys

# Generate keys if they don't exist
if [ ! -f /etc/keys/machine.privkey ] || [ ! -f /etc/keys/machine.pubkey ]; then
  certool --genkey --privkey=/etc/keys/machine.privkey --pubkey=/etc/keys/machine.pubkey \
    --srp-upn administrator@{{{LIGHTWAVE_DOMAIN}}} --srp-pwd {{{LIGHTWAVE_PASSWORD}}} --server {{{LIGHTWAVE_HOSTNAME}}}
fi

# Generate certificate if it doesn't exist
if [ ! -f /etc/keys/machine.crt ]; then
  certool --gencert --privkey=/etc/keys/machine.privkey --cert=/etc/keys/machine.crt \
    --srp-upn administrator@{{{LIGHTWAVE_DOMAIN}}} --srp-pwd {{{LIGHTWAVE_PASSWORD}}} \
    --server {{{LIGHTWAVE_HOSTNAME}}} --config /opt/vmware/share/config/certool.cfg
fi

# Generate pkcs12 keystore
openssl pkcs12 -export -in /etc/keys/machine.crt -inkey /etc/keys/machine.privkey -out keystore.p12 -name MACHINE_CERT \
  -password pass:{{{LIGHTWAVE_PASSWORD}}}

# Convert it into JKS
keytool -importkeystore -deststorepass {{{LIGHTWAVE_PASSWORD}}} -destkeypass {{{LIGHTWAVE_PASSWORD}}} \
  -destkeystore /keystore.jks -srckeystore keystore.p12 -srcstoretype PKCS12 -srcstorepass {{{LIGHTWAVE_PASSWORD}}} \
  -alias MACHINE_CERT

# Restrict permission on the key files
chmod 0400 /etc/keys/machine.privkey
chmod 0444 /etc/keys/machine.pubkey
{{/ENABLE_AUTH}}

#
# Start service
#
hash photon-controller-core 2>/dev/null
if [ $? -eq 1 ]; then
  COMMAND=$PHOTON_CONTROLLER_CORE_BIN/photon-controller-core
else
  COMMAND=photon-controller-core
fi

$COMMAND $PHOTON_CONTROLLER_CORE_CONFIG
