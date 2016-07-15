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
    module VirtualNetworkApi

      # @param [String] project_id
      # @param [VirtualNetworkCreateSpec] payload
      # @return [VirtualNetwork]
      def create_virtual_network(project_id, payload)
        @api_client.create_virtual_network project_id, payload
      end

      # @param [String] network_id
      # @return [Boolean]
      def delete_virtual_network(network_id)
        @api_client.delete_virtual_network network_id
      end


      # @return [VirtualNetworkList]
      def find_all_virtual_networks
        @api_client.find_all_virtual_networks
      end

      # @param [String] network_id
      # @return [VirtualNetwork]
      def find_virtual_network_by_id(network_id)
        @api_client.find_virtual_network_by_id network_id
      end

      # @param [String] project_id
      # @param [String] name
      # return [VirtualNetworkList]
      def find_virtual_networks_by_name(project_id, name)
        @api_client.find_virtual_networks_by_name project_id, name
      end
    end
  end
end
