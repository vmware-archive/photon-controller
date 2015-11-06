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
  class CliClient
    module ResourceTicketApi

      # @param [String] tenant_id
      # @param [Hash] payload
      # @return [ResourceTicket]
      def create_resource_ticket(tenant_id, payload)
        tenant = find_tenant_by_id(tenant_id)

        cmd = "resource-ticket create -t '#{tenant.name}' -n '#{payload[:name]}'"
        limits = payload[:limits].map { |limit|
          "#{limit[:key]} #{limit[:value]} #{limit[:unit]}"
        }.join(", ")
        cmd += " -l '#{limits}'"

        run_cli(cmd)
        find_resource_tickets_by_name(tenant_id, payload[:name]).items[0]
      end

      # @param [String] tenant_id
      # @return [ResourceTicketList]
      def find_all_resource_tickets(tenant_id)
        @api_client.find_all_resource_tickets(tenant_id)
      end

      # @param [String] tenant_id
      # @param [String] name
      # @return [ResourceTicketList]
      def find_resource_tickets_by_name(tenant_id, name)
        @api_client.find_resource_tickets_by_name(tenant_id, name)
      end

      # @param [String] id
      # @return [ResourceTicket]
      def find_resource_ticket_by_id(id)
        @api_client.find_resource_ticket_by_id(id)
      end
    end
  end
end
