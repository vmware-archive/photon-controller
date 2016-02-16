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
    module VmApi

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Vm]
      def create_vm(project_id, payload)
        project = find_project_by_id(project_id)
        tenant = @project_to_tenant[project.id]

        cmd = "vm create -t '#{tenant.name}' -p '#{project.name}' -n '#{payload[:name]}' -f '#{payload[:flavor]}' -i '#{payload[:sourceImageId]}'"

        disks = payload[:attachedDisks].map do |disk|
          disk_string = disk[:name] + " " + disk[:flavor]
          disk_string += " boot=true" if disk[:bootDisk]
          disk_string += " " + disk[:capacityGb].to_s if disk[:capacityGb]
          disk_string
        end.join(", ")

        cmd += " -d '#{disks}'"

        if payload[:environment]
          env = payload[:environment].map do |(k, v)|
            "#{k}:#{v}"
          end.join(", ")

          cmd += " -e '#{env}'"
        end

        if payload[:affinities] && !payload[:affinities].empty?
          affinities = payload[:affinities].map do |affinities|
            "#{affinities[:kind]}:#{affinities[:id]}"
          end.join(", ")

          cmd += " -a '#{affinities}'"
        end

        vm_id = run_cli(cmd)

        vm = find_vm_by_id(vm_id)
        @vm_to_project[vm.id] = project

        vm
      end

      # @param [String] id
      # @return [Boolean]
      def delete_vm(id)
        run_cli("vm delete '#{id}'")
        true
      end

      # @param [String] project_id
      # @return [VmList]
      def find_all_vms(project_id)
        @api_client.find_all_vms(project_id)
      end

      # @param [String] id
      # @return [Vm]
      def find_vm_by_id(id)
        cmd = "vm show #{id}"
        result = run_cli(cmd)
        get_vm_from_response(result)
      end

      # @param [String] project_id
      # @param [String] name
      # @return [VmList]
      def find_vms_by_name(project_id, name)
        @api_client.find_vms_by_name(project_id, name)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_vm_tasks(id, state = nil)
        @api_client.get_vm_tasks(id, state)
      end

      # @param [String] id
      # @return [VmNetworks]
      def get_vm_networks(id)
        @api_client.get_vm_networks(id)
      end

      # @param [String] id
      # @return [MksTicket]
      def get_vm_mks_ticket(id)
        @api_client.get_vm_mks_ticket(id)
      end

      # @param [String] id
      # @return [Vm]
      def start_vm(id)
        vm_id = run_cli("vm start '#{id}'")
        find_vm_by_id(vm_id)
      end

      # @param [String] id
      # @return [Vm]
      def stop_vm(id)
        vm_id = run_cli("vm stop '#{id}'")
        find_vm_by_id(vm_id)
      end

      # @param [String] id
      # @return [Vm]
      def restart_vm(id)
        vm_id = run_cli("vm restart '#{id}'")
        find_vm_by_id(vm_id)
      end

      # @param [String] id
      # @return [Vm]
      def resume_vm(id)
        vm_id = run_cli("vm resume '#{id}'")
        find_vm_by_id(vm_id)
      end

      # @param [String] id
      # @return [Vm]
      def suspend_vm(id)
        vm_id = run_cli("vm suspend '#{id}'")
        find_vm_by_id(vm_id)
      end

      # @param [String] id
      # @param [String] operation
      # @param [String] disk_id
      # @param [Hash] _
      # @return [Vm]
      def perform_vm_disk_operation(id, operation, disk_id, _ = {})
        operation_cmd = operation.downcase.sub('_','-')
        vm_id = run_cli("vm #{operation_cmd} '#{id}' -d '#{disk_id}'")
        find_vm_by_id(vm_id)
      end

      # @param [String] id
      # @param [String] iso_path
      # @param [String] iso_name
      # @return [Vm]
      def perform_vm_iso_attach(id, iso_path, iso_name = nil)
        @api_client.perform_vm_iso_attach(id, iso_path, iso_name)
      end

      # @param [String] id
      # @return [Vm]
      def perform_vm_iso_detach(id)
        vm_id = run_cli("vm detach-iso '#{id}'")
        find_vm_by_id(vm_id)
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Vm]
      def perform_vm_metadata_set(id, payload)
        vm_id = run_cli("vm set-metadata '#{id}' -m '#{payload[:metadata]}'")
        find_vm_by_id(vm_id)
      end

      private

      # @param [String] result
      # @return [Vm]
      def get_vm_from_response(result)
        values = result.split("\n")
        vm_attributes = values[0].split
        vm_hash = { "id" => vm_attributes[0],
                    "name" => vm_attributes[1],
                    "state" => vm_attributes[2],
                    "flavor" => vm_attributes[3],
                    "sourceImageId" => vm_attributes[4],
                    "host" => vm_attributes[5],
                    "datastore" => vm_attributes[6],
                    "metadata" => metadata_to_hash(vm_attributes[7]),
                    "tags" => tag_to_array(vm_attributes[8])}

        if values[1].to_i > 0
          vm_hash.merge!({"attachedDisks" => getAttachedDisks(values[2])})
        end

        if values[3].to_i > 0
          vm_hash.merge!("attachedIsos" => getAttachedISOs(values[4]))
        end

        Vm.create_from_hash(vm_hash)
      end

      def getAttachedDisks(result)
        attachedDisks = result.split(",").map do |attachedDisk|
          diskToHash(attachedDisk)
        end

        attachedDisks
      end

      def diskToHash(attachedDisk)
        disk_attributes = attachedDisk.split
        disk_hash = { "id" => disk_attributes[0],
                      "name" => disk_attributes[1],
                      "kind" => disk_attributes[2],
                      "flavor" => disk_attributes[3],
                      "capacityGb" => disk_attributes[4].to_i,
                      "bootDisk" => to_boolean(disk_attributes[5])}
        disk_hash
      end

      def getAttachedISOs(result)
        attachedISOs = result.split(",").map do |attachedISO|
          isoToHash(attachedISO)
        end

        attachedISOs
      end

      def isoToHash(attachedISO)
        iso_attributes = attachedISO.split
        iso_hash = { "id" => iso_attributes[0],
                     "name" => iso_attributes[1],
                     "kind" => iso_attributes[2],
                     "size" => iso_attributes[3].to_i}
        iso_hash
      end

      def to_boolean(str)
        str == "true"
      end

      # @param [String] metadata
      # @return hash
      def metadata_to_hash(metadata)
        hash_new = Hash.new
        if metadata.to_s != ''
          metadata.split(',').each { |attribute|
            values = attribute.split(':')
            hash_new.merge!({ values[0] => values[1]})}
        end
        hash_new
      end

      def tag_to_array(result)
        values = Array.new
        if result.to_s != ''
          values = result.split(',')
        end
        values
      end
    end
  end
end
