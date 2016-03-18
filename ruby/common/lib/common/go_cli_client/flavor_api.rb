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
        cmd = "flavor show #{id}"
        result = run_cli(cmd)
        get_flavor_from_response(result)
      end

      # @return [FlavorList]
      def find_all_flavors()
        @api_client.find_all_flavors
      end

      # @param [String] name
      # @param [String] kind
      # @return [FlavorList]
      def find_flavors_by_name_kind(name, kind)
        result = run_cli("flavor list -n '#{name}' -k '#{kind}'")

        get_flavor_list_from_response result
      end

      # @param [String] name
      # @param [String] kind
      # @return [FlavorList]
      def delete_flavor_by_name_kind(name, kind)
        @api_client.delete_flavor_by_name_kind(name, kind)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_flavor(id)
        run_cli("flavor delete '#{id}'")
        true
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_flavor_tasks(id, state = nil)
        cmd = "flavor tasks '#{id}'"
        cmd += "  -s '#{state}'" if state

        result = run_cli(cmd)
        get_task_list_from_response(result)
      end

      private

      # @param [String] result
      # @return Flavor
      def get_flavor_from_response(result)
        result.slice! "\n"
        values = result.split("\t", -1)
        flavor_hash = Hash.new
        flavor_hash["id"]    = values[0] unless values[0] == ""
        flavor_hash["name"]  = values[1] unless values[1] == ""
        flavor_hash["kind"]  = values[2] unless values[2] == ""
        flavor_hash["cost"]  = cost_to_hash(values[3])
        flavor_hash["state"] = values[4] unless values[4] == ""

        Flavor.create_from_hash(flavor_hash)
      end

      def get_flavor_list_from_response(result)
        flavors = result.split("\n").map do |flavor_info|
          get_flavor_details flavor_info.split("\t")[0]
        end

        FlavorList.new(flavors.compact)
      end

      def get_flavor_details(flavor_id)
        begin
          find_flavor_by_id flavor_id

          # When listing all flavors, if a flavor gets deleted
          # handle the Error to return nil for that flavor to
          # create flavor list for the flavors that exist.
        rescue EsxCloud::CliError => e
          raise() unless e.message.include? "FlavorNotFound"
          nil
        end
      end

      # @param [String] costs
      # @return hash
      def cost_to_hash(costs)
        costs_New = Array.new
        if costs.to_s != ''
          costs.split(',').each { |cost|
            values = cost.split(':')
            costs_New.push({ "key" => values[0], "value" => values[1].to_f, "unit" => values[2]})}
        end
        costs_New
      end
    end
  end
end
