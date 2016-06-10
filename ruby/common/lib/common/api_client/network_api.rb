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
    module NetworkApi

      NETWORKS_ROOT = "/subnets"

      # @param [Hash] payload
      # @return [Network]
      def create_network(payload)
        response = @http_client.post_json(NETWORKS_ROOT, payload)
        check_response("Create Network #{payload}", response, 201)

        task = poll_response(response)
        find_network_by_id(task.entity_id)
      end

      # @return [NetworkList]
      def find_all_networks
        response = @http_client.get(NETWORKS_ROOT)
        check_response("Find all networks", response, 200)

        NetworkList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Network]
      def find_network_by_id(id)
        response = @http_client.get("#{NETWORKS_ROOT}/#{id}")
        check_response("Find network by ID '#{id}'", response, 200)

        Network.create_from_json(response.body)
      end

      # @param [String] name
      # return [NetworkList]
      def find_networks_by_name(name)
        response = @http_client.get("#{NETWORKS_ROOT}?name=#{name}")
        check_response("Find Networks by name '#{name}'", response, 200)

        NetworkList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_network(id)
        response = @http_client.delete("#{NETWORKS_ROOT}/#{id}")
        check_response("Delete network '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @param [Array<String>] portgroups
      # @return [Network]
      def set_portgroups(id, portgroups)
        response = @http_client.post_json("#{NETWORKS_ROOT}/#{id}/set_portgroups", {items: portgroups})
        check_response("Update Network #{id} port groups #{portgroups}", response, 201)

        task = poll_response(response)
        find_network_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Boolean]
      def set_default(id)
        response = @http_client.post("#{NETWORKS_ROOT}/#{id}/set_default")
        check_response("Set Default Network #{id}", response, 201)

        poll_response(response)
        true
      end

    end
  end
end
