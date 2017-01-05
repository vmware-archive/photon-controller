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
    module ConfigApi

      CONFIG_URL = "/config"

      # @param [String] key
      # @return [Config]
      def find_config_by_key(key)
        response = @http_client.get("#{CONFIG_URL}/#{key}")
        check_response("Find config by KEY '#{key}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] key
      def delete_config_by_key(key)
        response = @http_client.delete("#{CONFIG_URL}/#{key}")
        check_response("Delete config by KEY '#{key}'", response, 200)
      end

      # @param [String] key
      # @param [String] value
      # @return [Config]
      def set_config_pair(key, value)
        response = @http_client.post("#{CONFIG_URL}/#{key}/#{value}", nil)
        check_response("Set config pair #{key} - #{value}", response, 200)
        JSON.parse(response.body)
      end
    end
  end
end
