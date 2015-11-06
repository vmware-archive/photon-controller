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
    module TenantApi
      # @param [Hash] payload
      # @return [Tenant]
      def create_tenant(payload)
        cmd = "tenant create '#{payload[:name]}'"
        security_groups = payload[:securityGroups]
        cmd += " -s '#{security_groups.join(",")}'" if security_groups
        run_cli(cmd)
        find_tenants_by_name(payload[:name]).items[0]
      end

      # @param [String] id
      # @return [Boolean]
      def delete_tenant(id)
        run_cli("tenant delete '#{id}'")
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
        @api_client.find_tenant_by_id(id)
      end

      # @return [TenantList]
      def find_all_tenants
        @api_client.find_all_tenants
      end

      # @param [String] name
      # @return [TenantList]
      def find_tenants_by_name(name)
        @api_client.find_tenants_by_name(name)
      end

      # @param [String] id
      # @return [TaskList]
      def get_tenant_tasks(id, state = nil)
        @api_client.get_tenant_tasks(id, state)
      end

      # @param [String] id
      # @return [TaskList]
      def get_resource_ticket_tasks(id, state = nil)
        @api_client.get_resource_ticket_tasks(id, state)
      end

      # @param [String] id
      # @param [Hash] payload
      def set_tenant_security_groups(id, payload)
        security_groups = payload[:items].join(",")
        cmd = "tenant set_security_groups #{id} #{security_groups}"
        run_cli(cmd)
      end
    end
  end
end
