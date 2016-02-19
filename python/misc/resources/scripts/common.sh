# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.
export SRVADMIN=/opt/dell/srvadmin

if [ "$(uname)" == "Darwin" ]; then
        # On OSX default BSD version of sed and readlink do not behave same as GNU versions.
        # brew install coreutils gnu-sed
        READLINK=greadlink
else
        READLINK=readlink
fi

# Use local wrappers where possible
if [ -z "$tools" ]; then
  tools=$($READLINK -nf $(dirname $BASH_SOURCE))
fi

export PATH=$tools:$tools/esxcli/lin32:$PATH
