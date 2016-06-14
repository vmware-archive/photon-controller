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
      def find_resource_tickets_by_name(tenant_id, name)
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
        resource_tickets = result.split("\n").map do |resource_ticket_info|
          get_resource_ticket_details resource_ticket_info.split("\t")[0]
        end

        ResourceTicketList.new(resource_tickets.compact)
      end

      def get_resource_ticket_details(resource_ticket_id)
        begin
          find_resource_ticket_by_id resource_ticket_id

          # When listing all resource tickets, if a resource ticket gets deleted
          # handle the Error to return nil for that resource ticket to
          # create resource ticket list for those that exist.
        rescue EsxCloud::CliError => e
          raise() unless e.message.include? "ResourceTicketNotFound"
          nil
        end
      end

      # @param [String] result
      # @param [String] tenant_id
      # @return [ResourceTicket]
      def get_resource_ticket_from_response(result, tenant_id)
        result.slice! "\n"
        values = result.split("\t", -1)
        resource_ticket_hash = Hash.new
        resource_ticket_hash["name"]     = values[0] unless values[0] == ""
        resource_ticket_hash["id"]       = values[1] unless values[1] == ""
        resource_ticket_hash["limits"]   = getLimitsOrUsage(values[2])
        resource_ticket_hash["usage"]    = getLimitsOrUsage(values[3])
        resource_ticket_hash["tenantId"] = tenant_id

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
