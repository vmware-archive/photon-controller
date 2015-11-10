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
        @api_client.create_availability_zone(payload)
      end

      # @param [String] id
      # @return AvailabilityZone
      def find_availability_zone_by_id(id)
        @api_client.find_availability_zone_by_id(id)
      end

      # @return [AvailabilityZoneList]
      def find_all_availability_zones()
        @api_client.find_all_availability_zones
      end

      # @param [String] id
      # @return [Boolean]
      def delete_availability_zone(id)
        @api_client.delete_availability_zone(id)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_availability_zone_tasks(id, state = nil)
        @api_client.get_availability_zone_tasks(id, state)
      end
    end
  end
end
