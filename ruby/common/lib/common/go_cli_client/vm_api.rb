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
        project = find_project_by_id(project_id)
        result = run_cli("vm list -p '#{project.name}' -n '#{name}'")
        get_vm_list_from_response(result)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_vm_tasks(id, state = nil)
        cmd = "vm tasks '#{id}'"
        cmd += " -s '#{state}'" if state

        result = run_cli(cmd)
        get_task_list_from_response(result)
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
        values = result.split("\n", -1)
        vm_attributes = values[0].split("\t", -1)
        vm_hash = Hash.new
        vm_hash["id"]            = vm_attributes[0] unless vm_attributes[0] == ""
        vm_hash["name"]          = vm_attributes[1] unless vm_attributes[1] == ""
        vm_hash["state"]         = vm_attributes[2] unless vm_attributes[2] == ""
        vm_hash["flavor"]        = vm_attributes[3] unless vm_attributes[3] == ""
        vm_hash["sourceImageId"] = vm_attributes[4] unless vm_attributes[4] == ""
        vm_hash["host"]          = vm_attributes[5] unless vm_attributes[5] == ""
        vm_hash["datastore"]     = vm_attributes[6] unless vm_attributes[6] == ""
        vm_hash["metadata"]      = metadata_to_hash(vm_attributes[7])
        vm_hash["tags"]          = stringToArray(vm_attributes[8])
        vm_hash["attachedDisks"] = getAttachedDisks(values[1])
        vm_hash["attachedIsos"]  = getAttachedISOs(values[2])

        Vm.create_from_hash(vm_hash)
      end

      def getAttachedDisks(result)
        attachedDisks = Array.new
        if result.to_s != ''
          attachedDisks = result.split(",").map do |attachedDisk|
           diskToHash(attachedDisk)
          end
        end

        attachedDisks
      end

      def diskToHash(attachedDisk)
        disk_attributes = attachedDisk.split("\t", -1)
        disk_hash = Hash.new
        disk_hash["id"]         = disk_attributes[0] unless disk_attributes[0] == ""
        disk_hash["name"]       = disk_attributes[1] unless disk_attributes[1] == ""
        disk_hash["kind"]       = disk_attributes[2] unless disk_attributes[2] == ""
        disk_hash["flavor"]     = disk_attributes[3] unless disk_attributes[3] == ""
        disk_hash["capacityGb"] = disk_attributes[4].to_i unless disk_attributes[4] == ""
        disk_hash["bootDisk"]   = to_boolean(disk_attributes[5])

        disk_hash
      end

      def getAttachedISOs(result)
        attachedISOs = Array.new
        if result.to_s != ''
         attachedISOs = result.split(",").map do |attachedISO|
            isoToHash(attachedISO)
          end
        end

        attachedISOs
      end

      def isoToHash(attachedISO)
        iso_attributes = attachedISO.split("\t", -1)
        iso_hash = Hash.new
        iso_hash["id"]   = iso_attributes[0] unless iso_attributes[0] == ""
        iso_hash["name"] = iso_attributes[1] unless iso_attributes[1] == ""
        iso_hash["kind"] = iso_attributes[2] unless iso_attributes[2] == ""
        iso_hash["size"] = iso_attributes[3].to_i unless iso_attributes[3] == ""

        iso_hash
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

      # @param [String] comma separated result
      # @return string array
      # @param [String] result
      # @return [Array]
      def stringToArray(result)
        values = Array.new
        if result.to_s != ''
          values = result.split(',')
        end
        values
      end

      # @param [String] str
      # @return bool
      def to_boolean(str)
        str == "true"
      end

      def get_vm_list_from_response(result)
        vms = result.split("\n").map do |vm_info|
          get_vm_details vm_info.split("\t")[0]
        end
        VmList.new(vms.compact)
      end

      def get_vm_details(vm_id)
        begin
          find_vm_by_id vm_id

          # When listing all vms, if a vm gets deleted
          # handle the Error to return nil for that vm to
          # create vm list for the vms that exist.
        rescue EsxCloud::CliError => e
          raise() unless e.message.include? "VmNotFound"
          nil
        end
      end
    end
  end
end
