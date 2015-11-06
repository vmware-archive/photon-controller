# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

module EsxCloud
  class ApiClient
    module AuthApi

      AUTH_ROOT = "/auth"

      # @return [AuthInfo]
      def get_auth_info
        response = @http_client.get(AUTH_ROOT)
        check_response("Get auth info", response, 200)
        AuthInfo.create_from_json(response.body)
      end

    end
  end
end
