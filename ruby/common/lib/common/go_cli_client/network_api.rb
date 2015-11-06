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
  class GoCliClient
    module NetworkApi
      # @param [Hash] payload
      # @return [Network]
      def create_network(payload)
        @api_client.create_network(payload)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_network(id)
        @api_client.delete_network(id)
      end

      # @param [String] id
      # @return [Network]
      def find_network_by_id(id)
        @api_client.find_network_by_id(id)
      end

      # @param [String] name
      # return [NetworkList]
      def find_networks_by_name(name)
        @api_client.find_networks_by_name(name)
      end

      # @return [NetworkList]
      def find_all_networks
        @api_client.find_all_networks
      end

      # @param [String] id
      # @param [Array<String>] portgroups
      # @return [Network]
      def set_portgroups(id, portgroups)
        @api_client.set_portgroups(id, portgroups)
      end

    end
  end
end
