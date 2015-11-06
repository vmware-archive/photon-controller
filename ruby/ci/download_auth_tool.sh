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

set -x
set -e

# get the working dir
if [ -z "$WORKSPACE" ]
then
    WORKSPACE=$(cd "$(dirname "$0")"; pwd)/../..
fi

# Get release job build number to download packages from
info_file="$(cd "$(dirname "$0")"; pwd)/release_build_info.bash"
auth_unzip_folder="auth_unzip_temp"

bundle install
bundle exec rake ci:export_job_url[$info_file]

cat $info_file
source $info_file

# Download artifacts from the lastSuccessfulBuild
rm -rf archive.zip archive/ $auth_unzip_folder cli/assets/
archive_url="${ARTIFACT_BUILD}artifact/*zip*/archive.zip"
if [ ! -z "$AUTH_TOOL_ARCHIVE_URL" ]
then
  archive_url="$AUTH_TOOL_ARCHIVE_URL"
fi

wget -nv $archive_url
unzip archive.zip

# Extract the auth tool and copy it under assets/
mkdir -p $auth_unzip_folder
tar_archives=(`find archive -name "auth-*.tar"`)
for tar_file in "${tar_archives[@]}"; do
  tar -xvf $tar_file -C $auth_unzip_folder
done

mkdir -p $WORKSPACE/ruby/cli/assets/
cp $auth_unzip_folder/auth-*/lib/auth-*.jar $WORKSPACE/ruby/cli/assets/

# Cleanup leftover directories
rm -rf archive.zip archive/ $auth_unzip_folder
