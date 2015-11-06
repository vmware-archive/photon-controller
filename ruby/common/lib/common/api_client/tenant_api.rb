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
    module TenantApi

      TENANTS_ROOT = "/tenants"

      # @param [Hash] payload
      # @return [Tenant]
      def create_tenant(payload)
        response = @http_client.post_json(TENANTS_ROOT, payload)
        check_response("Create tenant (#{payload})", response, 201)

        task = poll_response(response)
        find_tenant_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_tenant(id)
        response = @http_client.delete("#{TENANTS_ROOT}/#{id}")
        check_response("Delete tenant '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] name
      # @return [Boolean]
      def delete_tenant_by_name(name)
        tenant = find_tenants_by_name(name).items[0]

        if tenant.nil?
          raise NotFound, "Tenant named '#{name}' not found"
        end

        delete_tenant(tenant.id)
      end

      # @param [String] id
      # @return [Tenant]
      def find_tenant_by_id(id)
        response = @http_client.get("#{TENANTS_ROOT}/#{id}")
        check_response("Get tenant by ID '#{id}'", response, 200)

        Tenant.create_from_json(response.body)
      end

      # @return [TenantList]
      def find_all_tenants
        response = @http_client.get(TENANTS_ROOT)
        check_response("Find all tenants", response, 200)

        TenantList.create_from_json(response.body)
      end

      # @param [String] name
      # @return [TenantList]
      def find_tenants_by_name(name)
        response = @http_client.get("#{TENANTS_ROOT}?name=#{name}")
        check_response("Get tenants by name '#{name}'", response, 200)

        TenantList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [TaskList]
      def get_tenant_tasks(id, state = nil)
        url = "#{TENANTS_ROOT}/#{id}/tasks"
        params = []
        params << "state=#{state}" if state
        url += "?#{params.join("&")}" unless params.empty?

        response = @http_client.get(url)
        check_response("Get tasks for tenant '#{id}'", response, 200)

        TaskList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [TaskList]
      def get_resource_ticket_tasks(id, state = nil)
        url = "/resource-tickets/#{id}/tasks"
        params = []
        params << "state=#{state}" if state
        url += "?#{params.join("&")}" unless params.empty?

        response = @http_client.get(url)
        check_response("Get tasks for resource ticket '#{id}'", response, 200)

        TaskList.create_from_json(response.body)
      end

      # @param [String] id
      # @param [Hash] payload
      def set_tenant_security_groups(id, payload)
        response = @http_client.post_json("#{TENANTS_ROOT}/#{id}/set_security_groups", payload)
        check_response("Set the security groups for tenant '#{id}'", response, 200)

        poll_response(response)
      end
    end
  end
end
