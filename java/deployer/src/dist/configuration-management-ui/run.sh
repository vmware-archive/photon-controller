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

export API_ORIGIN="http://{{{APIFE_IP}}}:{{{APIFE_PORT}}}"

if [ -n "$ENABLE_AUTH" -a "$ENABLE_AUTH" == "true" ]
then
  export API_ORIGIN="https://{{{APIFE_IP}}}:{{{APIFE_PORT}}}"
  export LOGINREDIRECTENDPOINT="$MGMT_UI_LOGIN_URL"
  export LOGOUTREDIRECTENDPOINT="$MGMT_UI_LOGOUT_URL"
fi

/bin/bash /etc/nginx/config.sh && nginx -g 'daemon off;'
