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

# Function to find key: in config file.
# Example call: get_config_value "/config.xml" "secretKey:"
function get_config_value ()
{
  file=$1

  # Key to find in the config file.
  # It would be provided with ':' at the end. Pass 'memoryMb:' to find memroyMB in container.
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
    echo "WARNING: Missing value for '${key}' in ${PHOTON_CONTROLLER_CORE_CONFIG}"
  fi
}

CONFIG_PATH_PARAM=$1
INSTALLATION_PATH_PARAM=$2
CONFIG_PATH=${CONFIG_PATH_PARAM:-"/etc/esxcloud"}
PHOTON_CONTROLLER_CORE_CONFIG="${CONFIG_PATH}/photon-controller-core.yml"

memoryMb=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG memoryMb:`
ENABLE_AUTH=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG enableAuth:`
LIGHTWAVE_DOMAIN=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG lightwaveDomain:`
LIGHTWAVE_HOSTNAME=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG lightwaveHostname:`
LIGHTWAVE_PASSWORD=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG keyStorePassword:`
LIGHTWAVE_DOMAIN_CONTROLLER=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG lightwaveDomainController:`
LIGHTWAVE_MACHINE_ACCOUNT=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG lightwaveMachineAccount:`
LIGHTWAVE_DISABLE_VMAFD_LISTENER=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG lightwaveDisableVmafdListener:`
REGISTRATION_ADDRESS=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG registrationAddress:`
PHOTON_CONTROLLER_CORE_INSTALL_DIRECTORY=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG installDirectory:`
LOG_DIRECTORY=`get_config_value $PHOTON_CONTROLLER_CORE_CONFIG logDirectory:`

print_warning_if_value_mssing "${REGISTRATION_ADDRESS}"    "registrationAddress"   "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LOG_DIRECTORY}"           "logDirectory"          "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${memoryMb}"                "memoryMb"              "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${ENABLE_AUTH}"             "enableAuth"            "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LIGHTWAVE_PASSWORD}"      "keyStorePassword"      "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LIGHTWAVE_DOMAIN}"        "lightwaveDomain"      "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LIGHTWAVE_HOSTNAME}"      "lightwaveHostname"    "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LIGHTWAVE_DOMAIN_CONTROLLER}"     "lightwaveDomainController"  "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LIGHTWAVE_MACHINE_ACCOUNT}"       "lightwaveMachineAccount"    "$PHOTON_CONTROLLER_CORE_CONFIG"
print_warning_if_value_mssing "${LIGHTWAVE_DISABLE_VMAFD_LISTENER}" "lightwaveDisableVmafdListener"    "$PHOTON_CONTROLLER_CORE_CONFIG"

API_BITS=${INSTALLATION_PATH_PARAM:-"/usr/lib/esxcloud/photon-controller-core"}
API_BIN="$API_BITS/bin"
API_LIB="$API_BITS/lib"
API_SWAGGER_JS_FILE="swagger-config.js"
API_SWAGGER_JS="$CONFIG_PATH/$API_SWAGGER_JS_FILE"
PHOTON_CONTROLLER_CORE_BIN="${PHOTON_CONTROLLER_CORE_INSTALL_DIRECTORY}/bin"
SCRIPT_LOG_DIRECTORY="${LOG_DIRECTORY}/script_logs"

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
jvm_mem=$(($memoryMb/2))


# Use the JKS keystore which has our certificate as the default java keystore
security_opts="-Djavax.net.ssl.trustStore=/keystore.jks"

export JAVA_OPTS="-Xmx${jvm_mem}m -Xms${jvm_mem}m -XX:+UseConcMarkSweepGC ${security_opts} $JAVA_DEBUG"

if [ -n "$ENABLE_AUTH" -a "${ENABLE_AUTH,,}" == "true" ]
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

if [ -n "$ENABLE_AUTH" -a "${ENABLE_AUTH,,}" == "true" ]
then
  ic_join_params=""
  if [ -n "${LIGHTWAVE_DOMAIN_CONTROLLER}" ]
  then
    ic_join_params="$ic_join_params --domain-controller ${LIGHTWAVE_DOMAIN_CONTROLLER}"
  fi

  if [ -n "${LIGHTWAVE_MACHINE_ACCOUNT}" ]
  then
    ic_join_params="$ic_join_params --machine-account-name ${LIGHTWAVE_MACHINE_ACCOUNT}"
  fi

  if [ "${LIGHTWAVE_DISABLE_VMAFD_LISTENER,,}" == "true" ]
  then
    ic_join_params="$ic_join_params --disable-afd-listener"
  fi

  if [ -n "${LIGHTWAVE_DOMAIN}" ]
  then
    ic_join_params="$ic_join_params --domain ${LIGHTWAVE_DOMAIN}"
  fi

  if [ -n "${LIGHTWAVE_PASSWORD}" ]
  then
    ic_join_params="$ic_join_params --password ${LIGHTWAVE_PASSWORD}"
  fi

  full_hostname="$(hostname -f)"

  # Check if lightwave server is up
  attempts=1
  reachable="false"
  total_attempts=50
  while [ $attempts -lt $total_attempts ] && [ $reachable != "true" ]; do
    http_code=$(curl -w "%{http_code}" -s -X GET --insecure https://$LIGHTWAVE_HOSTNAME)
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
  ic-join ${ic_join_params}

  # Fill in the hostname and ip address for generating a machine certificate
  sed -i s/IPAddress.*/"IPAddress = ${REGISTRATION_ADDRESS}"/ /opt/vmware/share/config/certool.cfg
  sed -i s/Hostname.*/"Hostname = $full_hostname"/ /opt/vmware/share/config/certool.cfg

  mkdir -p /etc/keys

  # Generate keys if they don't exist
  if [ ! -f /etc/keys/machine.privkey ] || [ ! -f /etc/keys/machine.pubkey ]; then
    certool --genkey --privkey=/etc/keys/machine.privkey --pubkey=/etc/keys/machine.pubkey \
      --srp-upn administrator@${LIGHTWAVE_DOMAIN} --srp-pwd ${LIGHTWAVE_PASSWORD} --server ${LIGHTWAVE_HOSTNAME}
  fi

  # Generate certificate if it doesn't exist
  if [ ! -f /etc/keys/machine.crt ]; then
    certool --gencert --privkey=/etc/keys/machine.privkey --cert=/etc/keys/machine.crt \
      --srp-upn administrator@${LIGHTWAVE_DOMAIN} --srp-pwd ${LIGHTWAVE_PASSWORD} \
      --server ${LIGHTWAVE_HOSTNAME} --config /opt/vmware/share/config/certool.cfg
  fi

  # Generate pkcs12 keystore
  openssl pkcs12 -export -in /etc/keys/machine.crt -inkey /etc/keys/machine.privkey -out keystore.p12 -name MACHINE_CERT \
    -password pass:${LIGHTWAVE_PASSWORD}

  # Convert it into JKS
  keytool -importkeystore -deststorepass ${LIGHTWAVE_PASSWORD} -destkeypass ${LIGHTWAVE_PASSWORD} \
    -destkeystore /keystore.jks -srckeystore keystore.p12 -srcstoretype PKCS12 -srcstorepass ${LIGHTWAVE_PASSWORD} \
    -alias MACHINE_CERT

  # Get the trusted roots certificate
  cert_alias=$(vecs-cli entry list --store TRUSTED_ROOTS | grep "Alias" | cut -d: -f2)
  vecs-cli entry getcert --store TRUSTED_ROOTS --alias $cert_alias --output /etc/keys/cacert.crt
  keytool -import -trustcacerts -alias TRUSTED_ROOTS -file /etc/keys/cacert.crt \
  -keystore /keystore.jks -storepass ${LIGHTWAVE_PASSWORD} -noprompt

  # Restrict permission on the key files
  chmod 0400 /etc/keys/machine.privkey
  chmod 0444 /etc/keys/machine.pubkey

fi

# Move vecs jar out of classpath
mkdir -p ${API_LIB}/ext

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
