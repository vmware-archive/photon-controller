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

CONFIG="/usr/lib/zookeeper/conf/zoo.cfg"
MYIDFILE="{{{ZOOKEEPER_DATA_DIRECTORY}}}/myid"

# current version of zk has a bug defining the wrong class path
export CLASSPATH=/usr/lib/zookeeper-3.4.8.jar:/usr/lib/log4j-1.2.16.jar:/usr/lib/jline-0.9.94.jar:/usr/lib/netty-3.7.0.Final.jar:/usr/lib/slf4j-api-1.6.1.jar:/usr/lib/slf4j-log4j12-1.6.1.jar

echo "zookeeper config file:"
cat $CONFIG

echo "zookeeper id file:"
if [ -f myid ]
then
  cp -f myid $MYIDFILE
  cat $MYIDFILE
else
  echo "myid file does not exit"
fi

{{#PAUSE_APIFE_BACKGROUND}}
if [[ ! -e pauseBackground ]]
then
  echo "Pausing apife background"

  /usr/bin/zkServer.sh start-foreground $CONFIG &
  ZK_PID=$!
  echo ZK_PID
  sleep 70
  /usr/bin/zkCli.sh <<EOF
    create /config
    create /config/apife
    create /config/apife/status PAUSED_BACKGROUND
    quit
EOF
  echo "Creating Pause file"
  mkdir pauseBackground
  echo "Pause file created"
  kill $ZK_PID
  echo "Zookeeper stopped"
fi
{{/PAUSE_APIFE_BACKGROUND}}
echo "Starting zookeeper"

exec /usr/bin/zkServer.sh start-foreground $CONFIG
