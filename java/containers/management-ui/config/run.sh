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

export LOAD_BALANCER_IP="{{{APIFE_IP}}}"
export LOAD_BALANCER_API_PORT="{{{APIFE_PORT}}}"
export LOAD_BALANCER_HTTPS_PORT="{{{MGMT_UI_HTTPS_PORT_ON_LB}}}";

if [ -n "$ENABLE_AUTH" -a "$ENABLE_AUTH" == "true" ]
then
  export LOGINREDIRECTENDPOINT="$MGMT_UI_LOGIN_URL"
  export LOGOUTREDIRECTENDPOINT="$MGMT_UI_LOGOUT_URL"
fi

/bin/bash /etc/nginx/config.sh && nginx -g 'daemon off;'
