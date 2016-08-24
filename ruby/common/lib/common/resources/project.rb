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
  class Project

    attr_accessor :id, :name, :tenant_id, :resource_ticket, :security_groups

    # @param [String] tenant_id
    # @param [ProjectCreateSpec] project_create_spec
    def self.create(tenant_id, project_create_spec)
      Config.client.create_project(tenant_id, project_create_spec.to_hash)
    end

    # @param [String] id
    # @return [Task]
    def self.delete(id)
      Config.client.delete_project(id)
    end

    # @param [String] id
    # @return [Project]
    def self.find_by_id(id)
      Config.client.find_project_by_id(id)
    end

    # @param [String] json
    # @return [Project]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Project]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name resourceTicket).to_set)
        raise UnexpectedFormat, "Invalid project hash: #{hash}"
      end

      new(hash["id"], hash["name"], ProjectTicket.create_from_hash(hash["resourceTicket"]), hash["securityGroups"])
    end

    # @param [String] id
    # @param [String] name
    # @param [ResourceTicket] resource_ticket
    # @param [Hash] security_groups
    def initialize(id, name, resource_ticket, security_groups = nil)
      @id = id
      @name = name
      @resource_ticket = resource_ticket
      @security_groups = security_groups
    end

    # @param [Hash] options
    # @option options [String] :name
    # @option options [String] :flavor
    # @option options [Array<Disk>] :disks
    # @option options [Hash] :environment
    # @option options [Array<Locality>] :affinities
    # @option options [Array<String>] :networks
    def create_vm(options = {})
      spec = VmCreateSpec.new(options[:name], options[:flavor], options[:image_id], options[:disks],
                              options[:environment], options[:affinities], options[:networks])
      Vm.create(@id, spec)
    end

    # @param [ClusterCreateSpec] spec
    def create_cluster(options = {})
      spec = ClusterCreateSpec.new(
        options[:name],
        options[:type],
        options[:vm_flavor],
        options[:disk_flavor],
        options[:network_id],
        options[:worker_count],
        options[:batch_size],
        options[:extended_properties])

      Cluster.create(@id, spec)
    end

    # @param [Hash] options
    # @option options [String] :name
    # @option options [String] :kind
    # @option options [String] :flavor
    # @option options [Int] :capacity_gb
    def create_disk(options = {})
      spec = DiskCreateSpec.new(options[:name], options[:kind], options[:flavor], options[:capacity_gb],
                                options[:affinities], options[:tags])
      Disk.create(@id, spec)
    end

    # @return [Task]
    def delete
      self.class.delete(@id)
    end

    def ==(other)
      @id = other.id && @name == other.name && @resource_ticket == other.resource_ticket
    end
  end
end
