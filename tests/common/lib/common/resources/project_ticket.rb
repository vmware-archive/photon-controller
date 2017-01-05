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
  class ProjectTicket

    attr_accessor :limits, :usage, :tenant_ticket_id, :tenant_ticket_name

    # @param [String] json
    # @return [ProjectTicket]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [ProjectTicket]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(usage limits tenantTicketId tenantTicketName).to_set)
        raise UnexpectedFormat, "Invalid project ticket hash: #{hash}"
      end

      limits = hash["limits"].map { |qli| QuotaLineItem.new(qli["key"], qli["value"], qli["unit"]) }
      usage = hash["usage"].map { |qli| QuotaLineItem.new(qli["key"], qli["value"], qli["unit"]) }
      new(limits, usage, hash["tenantTicketId"], hash["tenantTicketName"])
    end

    def initialize(limits, usage, tenant_ticket_id, tenant_ticket_name)
      @limits = limits
      @usage = usage
      @tenant_ticket_id = tenant_ticket_id
      @tenant_ticket_name = tenant_ticket_name
    end

    def ==(other)
      @limits == other.limits && @usage == other.usage && @tenant_ticket_id == other.tenant_ticket_id &&
          @tenant_ticket_name == other.tenant_ticket_name
    end

  end
end

