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
    module ResourceTicketApi

      RESOURCE_TICKETS_ROOT = "/resource-tickets"

      # @param [String] tenant_id
      # @param [Hash] payload
      # @return [ResourceTicket]
      def create_resource_ticket(tenant_id, payload)
        response = @http_client.post_json("/tenants/#{tenant_id}/resource-tickets", payload)
        check_response("Create resource ticket (#{tenant_id}, #{payload})", response, 201)

        task = poll_response(response)
        find_resource_ticket_by_id(task.entity_id)
      end

      # @param [String] tenant_id
      # @return [ResourceTicketList]
      def find_all_resource_tickets(tenant_id)
        response = @http_client.get("/tenants/#{tenant_id}/resource-tickets")
        check_response("Find all resource tickets for tenant '#{tenant_id}'", response, 200)

        ResourceTicketList.create_from_json(response.body)
      end

      # @param [String] tenant_id
      # @param [String] name
      # @return [ResourceTicketList]
      def find_resource_tickets_by_name(tenant_id, name)
        response = @http_client.get("/tenants/#{tenant_id}/resource-tickets?name=#{name}")
        check_response("Find resource tickets by name '#{name}' for tenant '#{tenant_id}'", response, 200)

        ResourceTicketList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [ResourceTicket]
      def find_resource_ticket_by_id(id)
        response = @http_client.get("#{RESOURCE_TICKETS_ROOT}/#{id}")
        check_response("Get resource ticket by ID '#{id}'", response, 200)

        ResourceTicket.create_from_json(response.body)
      end
    end
  end
end
