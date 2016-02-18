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
if [ ! -f "pauseset.ini" ]
then
  echo "Pausing apife background"
  exec /usr/bin/zkServer.sh start-foreground $CONFIG &
  sleep 70
  /usr/bin/zkCli.sh  <<EOF
    create /config/apife
    create /config/apife/status PAUSED_BACKGROUND
    quit
EOF
  exec touch pauseset.ini
fi
{{/PAUSE_APIFE_BACKGROUND}}
{{^PAUSE_APIFE_BACKGROUND}}
exec /usr/bin/zkServer.sh start-foreground $CONFIG
{{/PAUSE_APIFE_BACKGROUND}}
