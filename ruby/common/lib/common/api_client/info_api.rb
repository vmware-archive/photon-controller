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
    module InfoApi

      # The /info API returns basic information about Photon
      # Controller: version & whether SDN is enabled

      # @return [Info]
      def get_info
       response = @http_client.get("/info")
       check_response("GET /info", response, 200)

       Info.create_from_json(response.body)
      end
    end
  end
end
