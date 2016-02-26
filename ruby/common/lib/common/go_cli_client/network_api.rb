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
        portgroups = payload[:portGroups]
        description = payload[:description]
        cmd = "network create -n '#{payload[:name]}' -p '#{portgroups.join(", ")}'"

        cmd += " -d '#{description}'" if description

        network_id = run_cli(cmd)

        find_network_by_id(network_id)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_network(id)
        run_cli("network delete '#{id}'")

        true
      end

      # @param [String] id
      # @return [Network]
      def find_network_by_id(id)
        result = run_cli("network show #{id}")

        get_network_from_response(result)
      end

      # @param [String] name
      # return [NetworkList]
      def find_networks_by_name(name)
        @api_client.find_networks_by_name(name)
      end

      # @return [NetworkList]
      def find_all_networks
        result = run_cli("network list")
        get_network_list_from_response(result)
      end

      # @param [String] id
      # @param [Array<String>] portgroups
      # @return [Network]
      def set_portgroups(id, portgroups)
        @api_client.set_portgroups(id, portgroups)
      end

      private

      def get_network_from_response(result)
        result.slice! "\n"
        values = result.split("\t")
        network_hash = { "id" =>values[0],
                         "name" => values[1],
                         "state" => values[2],
                         "portGroups" =>stringToArray(values[3]),
                         "description" => values[4]
                       }
        Network.create_from_hash(network_hash)
      end

      def get_network_list_from_response(result)
        networks = result.split("\n").drop(1).map do |network_info|
          find_network_by_id(network_info.split("\t")[0])
        end
        NetworkList.new(networks)
      end

      # @param [String] result
      # @return [Array]
      def stringToArray(result)
        values = Array.new
        if result.to_s != ''
          values = result.split(',')
        end
        values
      end
    end
  end
end
