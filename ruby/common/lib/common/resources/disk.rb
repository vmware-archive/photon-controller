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
  class Disk

    attr_reader :id, :name, :kind, :flavor, :capacity_gb, :datastore, :state, :vms, :tags

    # @param [String] project_id
    # @param [CreateVmSpec] create_vm_spec
    # @return [Disk]
    def self.create(project_id, create_disk_spec)
      Config.client.create_disk(project_id, create_disk_spec.to_hash)
    end

    def self.delete(disk_id)
      Config.client.delete_disk(disk_id)
    end

    # @param [String] json
    # @return [Disk]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Disk]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name kind flavor capacityGb state).to_set)
        raise UnexpectedFormat, "Invalid Disk hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["kind"], hash["flavor"], hash["capacityGb"], hash["datastore"],
          hash["state"], hash["vms"], hash["tags"])
    end

    # @param [String] id
    # @param [String] name
    # @param [String] kind
    # @param [String] flavor
    # @param [Int] capacity_gb
    # @param [String] datastore
    # @param [String] state
    # @param [Array<String>] vms
    def initialize(id, name, kind, flavor, capacity_gb, datastore, state = "DETACHED", vms, tags)
      @id = id
      @name = name
      @kind = kind
      @flavor = flavor
      @capacity_gb = capacity_gb
      @datastore = datastore
      @state = state
      @vms = vms
      @tags = tags
    end

    def delete
      self.class.delete(@id)
    end

    def ==(other)
      @id == other.id && @name == other.name && @kind == other.kind &&
      @flavor == other.flavor && @capacity_gb == other.capacity_gb &&
          @datastore == other.datastore && @state == other.state
    end

  end
end
