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
    module FlavorApi

      FLAVORS_ROOT = "/flavors"

      # @param [Hash] payload
      # @return [Flavor]
      def create_flavor(payload)
        response = @http_client.post_json(FLAVORS_ROOT, payload)
        check_response("Create flavor (#{payload})", response, 201)

        task = poll_response(response)
        find_flavor_by_id(task.entity_id)
      end

      # @param [String] id
      # @return Flavor
      def find_flavor_by_id(id)
        response = @http_client.get("#{FLAVORS_ROOT}/#{id}")
        check_response("Get flavor by id '#{id}'", response, 200)

        Flavor.create_from_json(response.body)
      end
      # @return [FlavorList]
      def find_all_flavors()
        response = @http_client.get(FLAVORS_ROOT)
        check_response("Find all flavors", response, 200)

        FlavorList.create_from_json(response.body)
      end

      # @param [String] name
      # @param [String] kind
      # @return [FlavorList]
      def find_flavors_by_name_kind(name, kind)
        response = @http_client.get("#{FLAVORS_ROOT}?name=#{name}&kind=#{kind}")
        check_response("Get flavors by name '#{name}', kind '#{kind}'", response, 200)

        FlavorList.create_from_json(response.body)
      end

      # @param [String] name
      # @param [String] kind
      # @return [FlavorList]
      def delete_flavor_by_name_kind(name, kind)
        flavor = find_flavors_by_name_kind(name, kind).items[0]

        if flavor.nil?
          raise NotFound, "Flavor named '#{name}', kind '#{kind}' not found"
        end

        delete_flavor(flavor.id)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_flavor(id)
        response = @http_client.delete("#{FLAVORS_ROOT}/#{id}")
        check_response("Delete flavor '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_flavor_tasks(id, state = nil)
        url = "#{FLAVORS_ROOT}/#{id}/tasks"
        url += "?state=#{state}" if state
        response = @http_client.get(url)
        check_response("Get tasks for flavor '#{id}'", response, 200)

        TaskList.create_from_json(response.body)
      end
    end
  end
end
