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
    module AvailableApi

      AVAILABLE_ROOT = "/available"

      # The /available API returns an empty response to indicate that
      # APIFE on a given host is up and running. It's intended to be
      # used by a load balancer in front of API-FE for a health check

      # @return [Available]
      def get_available
       response = @http_client.get("/available")
       check_response("GET /available", response, 200)

       Available.create_from_json(response.body)
      end
    end
  end
end
