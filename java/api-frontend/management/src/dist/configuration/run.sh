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

en_name=$(ip addr show label "en*" | head -n 1 | sed 's/^[0-9]*: \(en.*\): .*/\1/')
container_ip=$(ifconfig $en_name | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')

CONFIG_PATH="/etc/esxcloud"

migrate_apife_db=$1
shift

API_BITS="/usr/lib/esxcloud/management-api"
API_BIN="$API_BITS/bin"
API_LIB="$API_BITS/lib"
API_CONFIG="$CONFIG_PATH/management-api.yml"
API_SWAGGER_JS_FILE="swagger-config.js"
API_SWAGGER_JS="$CONFIG_PATH/$API_SWAGGER_JS_FILE"

#
# Same process is used for liquibase migrations
#
if [ -z "$migrate_apife_db" ]
then
  start_command="$API_BIN/management server $API_CONFIG"
else
  start_command="$API_BIN/management db migrate $API_CONFIG"
fi

jvm_mem=128

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
mv $API_SWAGGER_JS $CONFIG_PATH/assets
$JAVA_HOME/bin/jar uf ${API_LIB}/swagger-ui*.jar -C $CONFIG_PATH assets/$API_SWAGGER_JS_FILE

#
# Add hosts entry to allow InetAddress.getLocalHost() to
# succeed when running container with --net=host
#
myhostname="$(hostname)"
if [ -z "$(grep $myhostname /etc/hosts)" ]
then
  echo "$container_ip     $myhostname" >> /etc/hosts
fi

#
# Start service
#
$start_command
