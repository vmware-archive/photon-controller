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

security_opts="-Djava.security.properties=/etc/vmware/java/vmware-override-java.security"
export JAVA_OPTS="-Xmx${jvm_mem}m -Xms${jvm_mem}m -XX:+UseConcMarkSweepGC ${security_opts} {{{JAVA_DEBUG}}}"

if [ -n "$ENABLE_AUTH" -a "$ENABLE_AUTH" == "true" ]
then
  printf "window.swaggerConfig = {\n  enableAuth: true,\n  swaggerLoginUrl: '%s',\n  swaggerLogoutUrl: '%s',\n};\n" \
    $SWAGGER_LOGIN_URL $SWAGGER_LOGOUT_URL > $API_SWAGGER_JS
fi

#
# Add parameters-modified swagger-config.js to the jar
#
mkdir -p $CONFIG_PATH/assets
mv $API_SWAGGER_JS $CONFIG_PATH/assets

# Adding the modified swagger js file to swagger-ui*.jar is removed for now because it no longer works with our move
# to installing JRE instead of JDK. This causes the script to fail and exit early when run as a systemd service.
# TODO(ashokc): fix this as a part of getting swagger UI to work in auth enabled deployment

set -x

# Generate cert from certool to use
sed -i s/127.0.0.1/"{{{REGISTRATION_ADDRESS}}}"/ /opt/vmware/share/config/certool.cfg
sed -i s/server.acme.com/"devbox.esxcloud"/ /opt/vmware/share/config/certool.cfg
/opt/vmware/bin/certool --genkey --privkey=/tmp/cert.privkey --pubkey=/tmp/cert.pubkey
sleep 55
/opt/vmware/bin/certool --gencert --privkey=/tmp/cert.privkey --cert=/tmp/cert --srp-upn administrator@esxcloud --srp-pwd LW.pass2 --server {{{AUTH_SERVER_ADDRESS}}} --config /opt/vmware/share/config/certool.cfg

/opt/vmware/bin/vecs-cli entry create --store MACHINE_SSL_CERT --alias cert --cert /tmp/cert --key /tmp/cert.privkey

/opt/vmware/bin/vecs-cli entry getcert --store MACHINE_SSL_CERT --alias cert --text
/opt/vmware/bin/vecs-cli entry getkey --store MACHINE_SSL_CERT --alias cert --text

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
