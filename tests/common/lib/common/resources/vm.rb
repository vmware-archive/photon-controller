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
  class Vm

    attr_reader :id, :name, :flavor, :state, :source_image_id, :host, :datastore, :disks , :isos, :metadata, :tags

    # @param [String] project_id
    # @param [CreateVmSpec] create_vm_spec
    # @return [Vm]
    def self.create(project_id, create_vm_spec)
      Config.client.create_vm(project_id, create_vm_spec.to_hash)
    end

    def self.delete(vm_id)
      Config.client.delete_vm(vm_id)
    end

    # @param [String] id
    # @return [Vm]
    def self.find_vm_by_id(vm_id)
      Config.client.find_vm_by_id(vm_id)
    end

    def self.start(vm_id)
      Config.client.start_vm(vm_id)
    end

    def self.stop(vm_id)
      Config.client.stop_vm(vm_id)
    end

    def self.restart(vm_id)
      Config.client.restart_vm(vm_id)
    end

    def self.suspend(vm_id)
      Config.client.suspend_vm(vm_id)
    end

    def self.resume(vm_id)
      Config.client.resume_vm(vm_id)
    end

    def self.attach_disk(vm_id, disk_id)
      Config.client.perform_vm_disk_operation(vm_id, "ATTACH_DISK", disk_id)
    end

    def self.detach_disk(vm_id, disk_id)
      Config.client.perform_vm_disk_operation(vm_id, "DETACH_DISK", disk_id)
    end

    def self.attach_iso(vm_id, path, name)
      Config.client.perform_vm_iso_attach(vm_id, path, name)
    end

    def self.detach_iso(vm_id)
      Config.client.perform_vm_iso_detach(vm_id)
    end

    def self.set_metadata(vm_id, metadata)
      Config.client.perform_vm_metadata_set(vm_id, metadata)
    end

    def self.acquire_floating_ip(vm_id, floating_ip_spec)
      if floating_ip_spec == nil
        floating_ip_spec = {}
      end

      Config.client.acquire_floating_ip(vm_id, floating_ip_spec)
    end

    def self.release_floating_ip(vm_id)
      Config.client.release_floating_ip(vm_id)
    end

    # @param [String] json
    # @return [Vm]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Vm]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name flavor state).to_set)
        raise UnexpectedFormat, "Invalid VM hash: #{hash}"
      end

      disks = []
      if hash["attachedDisks"] && hash["attachedDisks"].is_a?(Enumerable)
        disks = hash["attachedDisks"].map do |disk|
          id = disk["id"] if ["persistent-disk", "persistent"].include? disk["kind"]
          VmDisk.new(disk["name"], disk["kind"], disk["flavor"], disk["capacityGb"], disk["bootDisk"], id)
        end
      end

      isos = []
      if hash["attachedIsos"] && hash["attachedIsos"].is_a?(Enumerable)
        isos = hash["attachedIsos"].map do |iso|
          Iso.new(iso["id"], iso["name"], iso["size"])
        end
      end

      new(hash["id"], hash["name"], hash["flavor"], hash["state"], hash["sourceImageId"], hash["host"], hash["datastore"], disks, isos, hash["metadata"], hash["tags"])
    end

    # @param [String] id
    # @param [String] name
    # @param [String] flavor
    # @param [String] state
    # @param [String] host
    # @param [String] datastore
    # @param [Array<VmDisk>] disks
    # @param [Hash] metadata
    def initialize(id, name, flavor, state, source_image_id, host, datastore, disks, isos, metadata = {}, tags)
      @id = id
      @name = name
      @flavor = flavor
      @state = state
      @source_image_id = source_image_id
      @host = host
      @datastore = datastore
      @disks = disks
      @isos = isos
      @metadata = metadata
      @tags = tags
    end

    def delete
      self.class.delete(@id)
    end

    def start!
      vm = self.class.start(@id)
      setup(vm)
    end

    def stop!
      vm = self.class.stop(@id)
      setup(vm)
    end

    def restart!
      vm = self.class.restart(@id)
      setup(vm)
    end

    def suspend!
      vm = self.class.suspend(@id)
      setup(vm)
    end

    def resume!
      vm = self.class.resume(@id)
      setup(vm)
    end

    def attach_disk(disk_id)
      vm = self.class.attach_disk(@id, disk_id)
      setup(vm)
    end

    def detach_disk(disk_id)
      vm = self.class.detach_disk(@id, disk_id)
      setup(vm)
    end

    def attach_iso(path, name = nil)
      vm = self.class.attach_iso(@id, path, name)
      setup(vm)
    end

    def detach_iso
      vm = self.class.detach_iso(@id)
      setup(vm)
    end

    # @param [hash] metadata
    def set_metadata(metadata)
      vm = self.class.set_metadata(@id, metadata)
      setup(vm)
    end

    def find_by_id
      self.class.find_vm_by_id(@id)
    end

    def get_attached_disk_names(kind)
      vm = self.class.find_vm_by_id(@id)
      if vm.nil?
        raise EsxCloud::NotFound
      end
      disk_names = []
      vm.disks.each do |disk|
        if (disk.kind == kind)
          disk_names.push(disk.name)
        end
      end
      disk_names
    end

    def get_metadata
      vm = self.class.find_vm_by_id(@id)
      vm.metadata
    end

    def acquire_floating_ip(floating_ip_spec)
      vm = self.class.acquire_floating_ip(@id, floating_ip_spec)
      setup(vm)
    end

    def release_floating_ip
      vm = self.class.release_floating_ip(@id)
      setup(vm)
    end

    def ==(other)
      @id == other.id &&
          @name == other.name &&
          @flavor == other.flavor &&
          @state == other.state &&
          @source_image_id == other.source_image_id &&
          @host == other.host &&
          @datastore == other.datastore &&
          @disks == other.disks &&
          @isos == other.isos &&
          @metadata == other.metadata
    end

    private

    def setup(vm)
      initialize(vm.id, vm.name, vm.flavor, vm.state, vm.source_image_id,
                 vm.host, vm.datastore, vm.disks, vm.isos, vm.metadata, vm.tags)
    end
  end
end
