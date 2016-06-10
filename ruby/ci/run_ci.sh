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

export PATH=/opt/ruby/bin:$PATH
export BUNDLE_PATH=/tmp/bundle

# run rubocop
cd $WORKSPACE/ruby/
bundle install
bundle exec rubocop

# run rubocop-photon specs
cd $WORKSPACE/ruby/rubocop-photon
bundle install
bundle exec rspec --format RspecJunitFormatter --out reports/rspec.xml

# Run the ruby rspecs
cd $WORKSPACE/ruby/common
bundle install
bundle exec rspec --format RspecJunitFormatter --out reports/rspec.xml
