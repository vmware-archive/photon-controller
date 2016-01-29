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
    module HostApi

      HOSTS_URL = "/resources/hosts"
      HOSTS_ROOT = "/hosts"

      # @param [String] deployment_id
      # @param [Hash] payload
      # @return [Host]
      def create_host(deployment_id, payload)
        response = @http_client.post_json("/deployments/#{deployment_id}/hosts", payload)
        check_response("Create host #{payload}", response, 201)
        task = poll_response(response)
        mgmt_find_host_by_id(task.entity_id)
      end

      # @return [HostList]
      def find_all_hosts()
        response = @http_client.get(HOSTS_URL)
        check_response("Find all hosts", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @return [Host]
      def find_host_by_id(id, property = nil, subId = nil)
        url = "#{HOSTS_URL}/#{id}"
        url = "#{url}/#{property}" if property
        url = "#{url}/#{property}/#{subId}" if property && subId
        response = @http_client.get(url)
        check_response("Find host by ID '#{id}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @return [Host]
      def mgmt_find_host_by_id(id)
        url = "#{HOSTS_ROOT}/#{id}"
        response = @http_client.get(url)
        check_response("Find host by ID '#{id}'", response, 200)
        Host.create_from_json(response.body)
      end

      # @param [String] id
      # @param [String] storeName
      # @param [String] storeId
      # @return [Host]
      def create_host_store(id, storeName, storeId)
        response = @http_client.post_json("#{HOSTS_URL}/#{id}/datastores/#{storeName}/#{storeId}", {})
        check_response("Adding store  #{storeName} #{storeId} to host #{id} ", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @param [String] storeName
      # @return [Host]
      def delete_host_store(id, storeName)
        response = @http_client.delete("#{HOSTS_URL}/#{id}/datastores/#{storeName}")
        check_response("Delete store  #{storeName} from host #{id} ", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @param [String] property
      # @param [String] value
      # @return [Metadata]
      def update_host_property(id, property, value)
        response = @http_client.put_json("#{HOSTS_URL}/#{id}/#{property}/#{value}", {})
        check_response("Update  property for host '#{id}' with  '#{property}' = '#{value}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @return [Metadata]
      def find_host_metadata_by_id(id)
        response = @http_client.get("#{HOSTS_URL}/#{id}/metadata")
        check_response("Find host metadata by ID '#{id}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @return [TaskList]
      def find_tasks_by_host_id(id)
        url = "#{HOSTS_ROOT}/#{id}/tasks"
        response = @http_client.get(url)
        check_response("Find tasks by host ID '#{id}'", response, 200)
        TaskList.create_from_json(response.body)
      end

      # @param [String] id
      # @param [String] property
      # @param [String] value
      # @return [Metadata]
      def update_host_metadata_property(id, property, value)
        response = @http_client.put_json("#{HOSTS_URL}/#{id}/metadata/#{property}/#{value}", {})
        check_response("Update  metadata property for host id '#{id}' with  '#{property}' = '#{value}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      def delete_host(id)
        response = @http_client.delete("#{HOSTS_URL}/#{id}")
        check_response("Delete host with id '#{id}'", response, 204)
      end

      # @param [String] id
      # @return [Boolean]
      def mgmt_delete_host(id)
        response = @http_client.delete("#{HOSTS_ROOT}/#{id}")
        check_response("Delete host with id '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @return [VmList]
      def mgmt_get_host_vms(id)
        response = @http_client.get("#{HOSTS_ROOT}/#{id}/vms")
        check_response("Get VMs for host '#{id}'", response, 200)
        VmList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Host]
      def host_enter_maintenance_mode(id)
        response = @http_client.post_json("#{HOSTS_ROOT}/#{id}/enter_maintenance", {})
        check_response("Put host '#{id}' into maintenance mode", response, 200)
        task = poll_response(response)
        mgmt_find_host_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Host]
      def host_enter_suspended_mode(id)
        response = @http_client.post_json("#{HOSTS_ROOT}/#{id}/suspend", {})
        check_response("Bring host '#{id}' into suspended mode", response, 200)
        task = poll_response(response)
        mgmt_find_host_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Host]
      def host_exit_maintenance_mode(id)
        response = @http_client.post_json("#{HOSTS_ROOT}/#{id}/exit_maintenance", {})
        check_response("Bring host '#{id}' out of maintenance mode", response, 200)
        task = poll_response(response)
        mgmt_find_host_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Host]
      def host_resume(id)
        response = @http_client.post_json("#{HOSTS_ROOT}/#{id}/resume", {})
        check_response("Resume host '#{id}' to normal mode", response, 200)
        task = poll_response(response)
        mgmt_find_host_by_id(task.entity_id)
      end

      # @param [String] host_id
      # @param [Hash] payload
      # @return [Host]
      def host_set_availability_zone(host_id, payload)
        response = @http_client.post_json("/hosts/#{host_id}/set_availability_zone", payload)
        check_response("Set host's availability zone #{payload}", response, 201)
        task = poll_response(response)
        mgmt_find_host_by_id(task.entity_id)
      end
    end
  end
end
