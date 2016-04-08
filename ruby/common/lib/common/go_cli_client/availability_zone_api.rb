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
    module AvailabilityZoneApi

      # @param [Hash] payload
      # @return [AvailabilityZone]
      def create_availability_zone(payload)
        cmd = "availability-zone create #{payload[:name]}"
        availability_zone_id = run_cli(cmd)

        find_availability_zone_by_id(availability_zone_id)
      end

      # @param [String] id
      # @return AvailabilityZone
      def find_availability_zone_by_id(id)
        result = run_cli("availability-zone show #{id}")

        get_availability_zone_from_response(result)
      end

      # @return [AvailabilityZoneList]
      def find_all_availability_zones()
        result = run_cli("availability-zone list")

        get_availability_zones_list_from_response(result)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_availability_zone(id)
        run_cli("availability-zone delete '#{id}'")
        true
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_availability_zone_tasks(id, state = nil)
        cmd = "availability-zone tasks '#{id}'"
        cmd += " -s '#{state}'" if state

        result = run_cli(cmd)
        get_task_list_from_response(result)
      end

      private

      def get_availability_zone_from_response(result)
        result.slice! "\n"
        values = result.split("\t", -1)
        availability_zone_hash = Hash.new
        availability_zone_hash["id"]    = values[0] unless values[0] == ""
        availability_zone_hash["name"]  = values[1] unless values[1] == ""
        availability_zone_hash["kind"]  = values[2] unless values[2] == ""
        availability_zone_hash["state"] = values[3] unless values[3] == ""

        AvailabilityZone.create_from_hash(availability_zone_hash)
      end

      def get_availability_zones_list_from_response(result)
        availability_zones = result.split("\n").map do |availability_zone_info|
          get_availability_zone_details availability_zone_info.split("\t")[0]
        end

        AvailabilityZoneList.new(availability_zones.compact)
      end

      # When listing all availability-zones, if availability-zone gets deleted
      # handle the Error to return nil for that availability-zone to
      # create availability-zone list for those that exist.
      def get_availability_zone_details(availability_zone_id)
        begin
          find_availability_zone_by_id availability_zone_id
        rescue EsxCloud::CliError => e
          raise() unless e.message.include? "AvailabilityZoneNotFound"
          nil
        end
      end
    end
  end
end
