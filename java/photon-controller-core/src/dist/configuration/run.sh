#!/bin/bash
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

ERROR_USAGE=1
ERROR_NOT_JOINED_TO_LIGHTWAVE=2
ERROR_FAILED_TO_GET_LIGHTWAVE_DC=3
ERROR_LIGHTWAVE_STS_UNREACHABLE=4

KEYS_ROOT_PATH=/etc/keys
MACHINE_SSL_CERT=$KEYS_ROOT_PATH/machine.crt
MACHINE_SSL_PRIVATE_KEY=$KEYS_ROOT_PATH/machine.privkey
MACHINE_SSL_PUBLIC_KEY=$KEYS_ROOT_PATH/machine.pubkey
CERT_TMP_PATH=$KEYS_ROOT_PATH/cacert.crt

PC_KEY_ROOT=/etc/vmware/photon
KEYSTORE_JKS_PATH=$PC_KEY_ROOT/keystore.jks
KEYSTORE_P12_PATH=$PC_KEY_ROOT/keystore.p12

function cleanup()
{
  if [ -f $MACHINE_SSL_PRIVATE_KEY ]
  then
    # Restrict permission on the key files
    chmod 0400 $MACHINE_SSL_PRIVATE_KEY
  fi
}

# Accept optional parameters for path of configuration and installation files
# Example usages: ./run.sh
#                 ./run.sh /opt/vmware/photon-controller/configuration
#                 ./run.sh /opt/vmware/photon-controller/configuration /opt/vmware/photon-controller/lib

# Function to find key: in config file.
# Example call: get_config_value "/config.xml" "secretKey:"
function get_config_value ()
{
  file=$1

  # Key to find in the config file.
  # It would be provided with ':' at the end. Pass 'memoryMb:' to find memoryMB in container.
  key=$2

  # Extract the "key: value" from the config file
  temp=`grep -m 1 $key $file`

  # Remove blank spaces around the string
  temp=`echo $temp | sed -e 's/^[[:blank:]]*//;s/*[[:blank:]]*$//'`

  # Remove the key from key:value pair
  temp=${temp#${key}}

  # Remove blank spaces around the string again after removing key
  temp=`echo $temp | sed -e 's/^[[:blank:]]*//;s/[[:blank:]]*$//'`

  # Remove first and last quotes
  temp="${temp%\"}"
  temp="${temp#\"}"

  # Return the value
  echo $temp
}

function print_warning_if_value_mssing ()
{
  value=$1
  key=$2
  config_file=$3
  if [ -z "${value}" ]
  then
    echo "INFO: Missing value for '${key}' in ${PHOTON_CONTROLLER_CORE_CONFIG}. Will continue without it."
  fi
}

function setup_swagger()
{
  local config_path=$1
  local install_path=$2

  local API_BITS=$install_path
  local API_BIN="$API_BITS/bin"
  local API_LIB="$API_BITS/lib"
  local API_SWAGGER_JS_FILE="swagger-config.js"
  local API_SWAGGER_JS="$config_path/$API_SWAGGER_JS_FILE"

  if [ ! -f $API_SWAGGER_JS ]
  then
    printf "window.swaggerConfig = {\n  enableAuth: true,\n  swaggerLoginUrl: '%s',\n  swaggerLogoutUrl: '%s',\n};\n" \
           $SWAGGER_LOGIN_URL \
           $SWAGGER_LOGOUT_URL \
           > $API_SWAGGER_JS

    #
    # Add parameters-modified swagger-config.js to the jar
    #
    mkdir -p $config_path/assets
    cp $API_SWAGGER_JS $config_path/assets

    $JAVA_HOME/bin/jar uf \
                       ${API_LIB}/swagger-ui*.jar \
                       -C $config_path \
                       assets/$API_SWAGGER_JS_FILE
  fi
}

function check_lightwave()
{
  domain_name=`/opt/vmware/bin/domainjoin info | \
                      awk -F ":" '{print $2;}' | \
                      sed "s/^[[:space:]]*//"`
  if [ -z "$domain_name"]
  then
    echo "Error: The system is not joined to Lightwave"
    exit $ERROR_NOT_JOINED_TO_LIGHTWAVE
  fi

  lightwave_dc=`/opt/vmware/bin/vmafd-cli get-dc-name --server-name localhost`

  if [ -z "$lightwave_dc"]
  then
    echo "Error: Failed to get Lightwave Domain Controller"
    exit $ERROR_FAILED_TO_GET_LIGHTWAVE_DC
  fi

  # Check if lightwave server is up
  attempts=1
  reachable="false"
  total_attempts=500
  while [ $attempts -lt $total_attempts ] && [ $reachable != "true" ]; do
    http_code=$(curl -w "%{http_code}" -s -X GET --insecure https://$lightwave_dc)
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
    exit $ERROR_LIGHTWAVE_STS_UNREACHABLE
  fi
}

function setup_certificates()
{
  mkdir -p $KEYS_ROOT_PATH

  if [ ! -f $MACHINE_SSL_PRIVATE_KEY ]
  then
    /opt/vmware/bin/vecs-cli entry getkey --store MACHINE_SSL_CERT \
                                          --alias '__MACHINE_CERT' \
                                          --output $MACHINE_SSL_PRIVATE_KEY

    # Restrict permission on the key files
    chmod 0400 $MACHINE_SSL_PRIVATE_KEY
    chmod 0444 $MACHINE_SSL_PUBLIC_KEY
  fi

  if [ ! -f $MACHINE_SSL_CERT ]
  then
    /opt/vmware/bin/vecs-cli entry getcert --store MACHINE_SSL_CERT \
                                           --alias '__MACHINE_CERT' \
                                           --output $MACHINE_SSL_CERT
  fi

  if [ ! -f $KEYSTORE_JKS_PATH ]
  then
    mkdir -p $PC_KEY_ROOT

    # Generate pkcs12 keystore
    openssl pkcs12 -export \
                   -in $MACHINE_SSL_CERT \
                   -inkey $MACHINE_SSL_PRIVATE_KEY \
                   -out $KEYSTORE_P12_PATH \
                   -name __MACHINE_CERT \
                   -password pass:${KEYSTORE_PASSWORD}

    chmod 0600 $KEYSTORE_P12_PATH

    # Convert it into JKS
    keytool -importkeystore \
            -deststorepass ${KEYSTORE_PASSWORD} \
            -destkeypass ${KEYSTORE_PASSWORD} \
            -destkeystore $KEYSTORE_JKS_PATH \
            -srckeystore $KEYSTORE_P12_PATH \
            -srcstoretype PKCS12 \
            -srcstorepass ${KEYSTORE_PASSWORD} \
            -alias __MACHINE_CERT

    chmod 0600 $KEYSTORE_JKS_PATH

    # Get the trusted roots certificates
    cert_alias=$(/opt/vmware/bin/vecs-cli entry list --store TRUSTED_ROOTS | \
                                                    grep "Alias" | \
                                                    cut -d: -f2)

    for cert in $cert_alias
    do
      echo "Processing cert $cert"

      /opt/vmware/bin/vecs-cli entry getcert --store TRUSTED_ROOTS \
                                             --alias $cert \
                                             --output $CERT_TMP_PATH

      keytool -import \
              -trustcacerts \
              -alias "$cert" \
              -file $CERT_TMP_PATH \
              -keystore $KEYSTORE_JKS_PATH \
              -storepass ${KEYSTORE_PASSWORD} \
              -noprompt
    done

    # Delete temporary cacert file.
    # curl command should be passed with --capath /etc/ssl/certs.
    rm -rf $CERT_TMP_PATH

  fi
}

#
# main
#

trap cleanup EXIT

set +e

if [ $# -gt 0 ]
then
  CONFIG_PATH_PARAM=$1
fi

if [ $# -gt 1 ]
then
  INSTALLATION_PATH_PARAM=$2
fi

CONFIG_PATH=${CONFIG_PATH_PARAM:-"/etc/esxcloud"}
INSTALL_PATH=${INSTALLATION_PATH_PARAM:-"/usr/lib/esxcloud/photon-controller-core"}
PHOTON_CONTROLLER_CORE_CONFIG="${CONFIG_PATH}/photon-controller-core.yml"

memoryMb=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG memoryMb:`
KEYSTORE_PASSWORD=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG keyStorePassword:`
REGISTRATION_ADDRESS=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG registrationAddress:`
PHOTON_CONTROLLER_CORE_INSTALL_DIRECTORY=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG installDirectory:`
LOG_DIRECTORY=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG logDirectory:`

print_warning_if_value_mssing "${REGISTRATION_ADDRESS}"    "registrationAddress"   "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LOG_DIRECTORY}"           "logDirectory"          "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${memoryMb}"                "memoryMb"              "$PHOTON_CONTROLLER_CORE_CONFIG"

PHOTON_CONTROLLER_CORE_BIN="${PHOTON_CONTROLLER_CORE_INSTALL_DIRECTORY}/bin"
SCRIPT_LOG_DIRECTORY="${LOG_DIRECTORY}/script_logs"

# Use the JKS keystore which has our certificate as the default java keystore
security_opts="-Djavax.net.ssl.trustStore=$KEYSTORE_JKS_PATH"

# jvm heap size will be set to by default is 1024m
jvm_mem=$(($memoryMb/2))

export JAVA_HOME="/usr/java/default"
export JAVA_OPTS="-Xmx${jvm_mem}m -Xms${jvm_mem}m -XX:+UseConcMarkSweepGC ${security_opts} $JAVA_DEBUG"

# Add java home and other required binaries to path.
# We need this here even though we are doing this during container build
# because systemd service does not seem to honor the environment variables
# set at container build time.
export PATH=$PATH:$JAVA_HOME/bin:/opt/esxcli:/opt/vmware/bin:/opt/likewise/bin

# Create script log directory
mkdir -p $SCRIPT_LOG_DIRECTORY

#
# Add hosts entry to allow InetAddress.getLocalHost() to
# succeed when running container with --net=host
#
en_name=$(ip addr show label "en*" | head -n 1 | sed 's/^[0-9]*: \(en.*\): .*/\1/')
container_ip=$(ifconfig $en_name | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')

myhostname="$(hostname)"
if [ -z "$(grep $myhostname /etc/hosts)" ]
then
  echo "$container_ip     $myhostname" >> /etc/hosts
fi

setup_swagger $CONFIG_PATH $INSTALL_PATH

check_lightwave

setup_certificates

#
# Start service
#
hash photon-controller-core 2>/dev/null
if [ $? -eq 1 ]; then
  COMMAND=${PHOTON_CONTROLLER_CORE_BIN}/photon-controller-core
else
  COMMAND=photon-controller-core
fi

$COMMAND $PHOTON_CONTROLLER_CORE_CONFIG

set -e

$COMMAND $PHOTON_CONTROLLER_CORE_CONFIG
