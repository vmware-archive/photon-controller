#!/bin/bash -x
# Copyright © 2015 VMware, Inc.  All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the “License”); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an “AS IS” BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

set -e

jarUrl=$1
jarFile=$2
jarTargetFolder=$3

jarFileDeletePattern=$jarFile
if [ ! -z $4 ]
then
  jarFileDeletePattern=$4
fi

jarDownloadFolder="$HOME/.esxcloud-build-cache/jar"

# try download the jar
downloaded=true
mkdir -p ${jarDownloadFolder}
pushd ${jarDownloadFolder}
wget --no-proxy -N -t 1 ${jarUrl} || downloaded=false
popd

if ! $downloaded
then
  echo "Failed to download, trying to use the cached copy ..."
fi

mkdir -p $jarTargetFolder
rm -f $jarTargetFolder/$jarFileDeletePattern
cp ${jarDownloadFolder}/${jarFile} ${jarTargetFolder}/${jarFile} || echo "Could not copy file from cached folder"

if [ ! -f "$jarTargetFolder/$jarFile" ]
then
  echo "JAR file does not exist"
  exit 1
fi
