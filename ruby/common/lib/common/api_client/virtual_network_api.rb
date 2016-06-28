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
    module VirtualNetworkApi

      # @param [String] project_id
      # @param [VirtualNetworkCreateSpec] payload
      # @return [VirtualNetwork]
      def create_virtual_network(project_id, payload)
        url = "/projects/#{project_id}/networks"
        response = @http_client.post_json(url, payload)
        check_response("Create virtual network #{payload}", response, 201)

        task = poll_response(response)
        find_virtual_network_by_id(task.entity_id)
      end

      # @param [String] network_id
      # @return [Boolean]
      def delete_virtual_network(network_id)
        response = @http_client.delete("/subnets/#{network_id}")
        check_response("Delete virtual subnet #{network_id}", response, 201)

        poll_response(response)
        true
      end

      # @param [String] network_id
      # @return [VirtualNetwork]
      def find_virtual_network_by_id(network_id)
        response = @http_client.get("/subnets/#{network_id}")
        check_response("Get virtual subnet #{network_id}", response, 200)

        VirtualNetwork.create_from_json(response.body)
      end

      # @param [String] name
      # return [VirtualNetworkList]
      def find_virtual_networks_by_name(name)
        response = @http_client.get("/networks/?name=#{name}")
        check_response("Find Networks by name '#{name}'", response, 200)

        puts "Get virtual subnet #{name}"
        puts response.inspect
        VirtualNetworkList.create_from_json(response.body)
      end

    end
  end
end
