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
  # This class is designed to help CLI test. All methods should call CLI command
  # through run_cli
  class GoCliClient
    module FlavorApi

      # @param [Hash] payload
      # @return Flavor
      def create_flavor(payload)
        cmd = "flavor create -n '#{payload[:name]}' -k '#{payload[:kind]}' "
        costs = payload[:cost].map { |cost|
          "#{cost[:key]} #{cost[:value]} #{cost[:unit]}"
        }.join(", ")
        cmd += "-c '#{costs}'"

        id = run_cli(cmd)
        find_flavor_by_id(id)
      end

      # @param [String] id
      # @return Flavor
      def find_flavor_by_id(id)
        #cmd = "flavor show #{id}"
        #result = run_cli(cmd)
        #get_flavor_from_cli_cmd(result)
       @api_client.find_flavor_by_id(id)
      end

      def get_flavor_from_cli_cmd(result)
        values = result.split()
        flavor_hash = { "id" => values[0], "name" => values[1], "kind" => values[2],
                        "cost" => cost_to_hash(values[3]), "state" => values[4] }
        Flavor.create_from_hash(flavor_hash)
      end

      def cost_to_hash(costs)
        costs_New = Array.new
        costs.split(',').each { |cost|
          values = cost.split(':')
          costs_New.push({ "key" => values[0], "value" => values[1], "unit" => values[2]})}
        costs_New
      end

      # @return [FlavorList]
      def find_all_flavors()
        @api_client.find_all_flavors
      end

      # @param [String] name
      # @param [String] kind
      # @return [FlavorList]
      def find_flavors_by_name_kind(name, kind)
        @api_client.find_flavors_by_name_kind(name, kind)
      end

      # @param [String] name
      # @param [String] kind
      # @return [FlavorList]
      def delete_flavor_by_name_kind(name, kind)
        run_cli("flavor delete -n '#{name}' -k '#{kind}'")
      end

      # @param [String] id
      # @return [Boolean]
      def delete_flavor(id)
        @api_client.delete_flavor(id)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_flavor_tasks(id, state = nil)
        @api_client.get_flavor_tasks(id, state)
      end
    end
  end
end
