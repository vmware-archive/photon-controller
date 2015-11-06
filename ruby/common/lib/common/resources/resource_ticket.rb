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
  class ResourceTicket

    attr_accessor :id, :name, :tenant_id, :limits, :usage

    # @param [String] tenant_id
    # @param [ResourceTicketCreateSpec] resource_ticket_create_spec
    # @return [ResourceTicket]
    def self.create(tenant_id, resource_ticket_create_spec)
      Config.client.create_resource_ticket(tenant_id, resource_ticket_create_spec.to_hash)
    end

    # @param [String] json
    # @return [ResourceTicket]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [ResourceTicket]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name tenantId limits usage).to_set)
        raise UnexpectedFormat, "Invalid resource ticket hash: #{hash}"
      end

      limits = hash["limits"].map { |qli| QuotaLineItem.new(qli["key"], qli["value"], qli["unit"]) }
      usage = hash["usage"].map { |qli| QuotaLineItem.new(qli["key"], qli["value"], qli["unit"]) }
      new(hash["id"], hash["tenantId"], hash["name"], limits, usage)
    end

    # @param [String] id
    # @return [ResourceTicket]
    def self.find_by_id(id)
      Config.client.find_resource_ticket_by_id(id)
    end

    # @param [String] id
    # @param [String] tenant_id
    # @param [String] name
    # @param [Array<QuotaLineItem>] limits
    # @param [Array<QuotaLineItem>] usage
    def initialize(id, tenant_id, name, limits, usage)
      @id = id
      @tenant_id = tenant_id
      @name = name
      @limits = limits
      @usage = usage
    end

    def ==(other)
      @id == other.id && @tenant_id == other.tenant_id &&
          @name == other.name && @limits == other.limits
    end

  end
end
