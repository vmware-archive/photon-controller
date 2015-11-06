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
  class Tenant

    attr_accessor :id, :name, :security_groups

    # @param [TenantCreateSpec] tenant_create_spec
    # @return [Tenant]
    def self.create(tenant_create_spec)
      Config.client.create_tenant(tenant_create_spec.to_hash)
    end

    # @param [String] id
    # @return [Task]
    def self.delete(id)
      Config.client.delete_tenant(id)
    end

    # @param [String] id
    # @return [Tenant]
    def self.find_by_id(id)
      Config.client.find_tenant_by_id(id)
    end

    # @param [String] name
    # @return [TenantList]
    def self.find_by_name(name)
      Config.client.find_tenants_by_name(name)
    end

    # @param [String] json
    # @return [Tenant]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Tenant]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name).to_set)
        raise UnexpectedFormat, "Invalid tenant hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["securityGroups"])
    end

    # @param [String] id
    # @param [String] name
    def initialize(id, name, security_groups = nil)
      @id = id
      @name = name
      @security_groups = security_groups
    end

    # @param[Hash] options
    # @option options [String] :name
    # @option options [Array<QuotaLineItem>] :limits
    # @return [ResourceTicket]
    def create_resource_ticket(options = {})
      spec = ResourceTicketCreateSpec.new(options[:name], options[:limits])
      ResourceTicket.create(@id, spec)
    end

    # @param [Hash] options
    # @option options [String] :name
    # @option options [String] :resource_ticket_name
    # @option options [String] :limits
    # @option options [Array<String>] :security_groups
    # @return [Project]
    def create_project(options = {})
      spec = ProjectCreateSpec.new(
          options[:name], options[:resource_ticket_name], options[:limits], options[:security_groups])
      Project.create(@id, spec)
    end

    # @return [Task]
    def delete
      self.class.delete(@id)
    end

    # @param [String] id
    # @param [Hash] payload
    def set_project_security_groups(id, payload)
      Config.client.set_project_security_groups(id, payload)
    end

    def ==(other)
      @id == other.id && @name == other.name
    end

  end
end
