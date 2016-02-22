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

        resource_ticket_id = run_cli(cmd)

        find_resource_ticket_by_id(resource_ticket_id)
      end

      # @param [String] tenant_id
      # @return [ResourceTicketList]
      def find_all_resource_tickets(tenant_id)
        tenant = find_tenant_by_id(tenant_id)

        cmd = "resource-ticket list -t '#{tenant.name}'"

        result = run_cli(cmd)
        get_resource_ticket_list_from_response(result)
      end

      # @param [String] tenant_id
      # @param [String] name
      # @return [ResourceTicket]
      def find_resource_ticket_by_name(tenant_id, name)
        tenant = find_tenant_by_id(tenant_id)

        cmd = "resource-ticket show '#{name}' -t '#{tenant.name}'"
        result = run_cli(cmd)
        get_resource_ticket_from_response(result,tenant_id)
      end

      # @param [String] id
      # @return [ResourceTicket]
      def find_resource_ticket_by_id(id)
        @api_client.find_resource_ticket_by_id(id)
      end

      private

      # @param [String] result
      # @param [String] tenant_id
      # @return [ResourceTicketList]
      def get_resource_ticket_list_from_response(result)
        resource_tickets = result.split("\n").drop(1).map do |resource_ticket_info|
          find_resource_ticket_by_id(resource_ticket_info.split("\t")[0])
        end

        ResourceTicketList.new(resource_tickets)
      end

      # @param [String] result
      # @param [String] tenant_id
      # @return [ResourceTicket]
      def get_resource_ticket_from_response(result, tenant_id)
        result.slice! "\n"
        values = result.split("\t")
        resource_ticket_hash = { "name" => values[0],
                                 "id" => values[1],
                                 "limits" => getLimitsOrUsage(values[2]),
                                 "usage" => getLimitsOrUsage(values[3]),
                                 "tenantId" => tenant_id}
        ResourceTicket.create_from_hash(resource_ticket_hash)
      end

      def getLimitsOrUsage(result)
        limitsOrUsages = Array.new
        if result.to_s != ''
          limitsOrUsages = result.split(",").map do |limitOrUsage|
            attributes = limitOrUsage.split(":")
            {"key" => attributes[0], "value" => attributes[1].to_f,"unit" => attributes[2]}
          end
        end

        limitsOrUsages
      end
    end
  end
end
