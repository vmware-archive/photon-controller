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

require 'yaml'

module EsxCloud
  class ApiClient
    module AvailabilityZoneApi

      AVAILABILITY_ZONES_ROOT = "/availabilityzones"

      # @param [Hash] payload
      # @return [AvailabilityZone]
      def create_availability_zone(payload)
        response = @http_client.post_json(AVAILABILITY_ZONES_ROOT, payload)
        check_response("Create availability zone (#{payload})", response, 201)

        task = poll_response(response)
        find_availability_zone_by_id(task.entity_id)
      end

      # @param [String] id
      # @return AvailabilityZone
      def find_availability_zone_by_id(id)
        response = @http_client.get("#{AVAILABILITY_ZONES_ROOT}/#{id}")
        check_response("Get availability zone by id '#{id}'", response, 200)

        AvailabilityZone.create_from_json(response.body)
      end

      # @return [AvailabilityZoneList]
      def find_all_availability_zones()
        response = @http_client.get(AVAILABILITY_ZONES_ROOT)
        check_response("Find all availability zones", response, 200)

        AvailabilityZoneList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_availability_zone(id)
        response = @http_client.delete("#{AVAILABILITY_ZONES_ROOT}/#{id}")
        check_response("Delete availability zone '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_availability_zone_tasks(id, state = nil)
        url = "#{AVAILABILITY_ZONES_ROOT}/#{id}/tasks"
        url += "?state=#{state}" if state
        response = @http_client.get(url)
        check_response("Get tasks for availability zone '#{id}'", response, 200)
        TaskList.create_from_json(response.body)
      end
    end
  end
end
