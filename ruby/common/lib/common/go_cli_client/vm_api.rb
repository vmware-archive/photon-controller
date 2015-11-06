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
        @api_client.create_vm(project_id, payload)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_vm(id)
        @api_client.delete_vm(id)
      end

      # @param [String] project_id
      # @return [VmList]
      def find_all_vms(project_id)
        @api_client.find_all_vms(project_id)
      end

      # @param [String] id
      # @return [Vm]
      def find_vm_by_id(id)
        @api_client.find_vm_by_id(id)
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
        @api_client.start_vm(id)
      end

      # @param [String] id
      # @return [Vm]
      def stop_vm(id)
        @api_client.stop_vm(id)
      end

      # @param [String] id
      # @return [Vm]
      def restart_vm(id)
        @api_client.restart_vm(id)
      end

      # @param [String] id
      # @return [Vm]
      def resume_vm(id)
        @api_client.resume_vm(id)
      end

      # @param [String] id
      # @return [Vm]
      def suspend_vm(id)
        @api_client.suspend_vm(id)
      end

      # @param [String] id
      # @param [String] operation
      # @param [String] disk_id
      # @param [Hash] _
      # @return [Vm]
      def perform_vm_disk_operation(id, operation, disk_id, _ = {})
        @api_client.perform_vm_disk_operation(id, operation, disk_id)
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
        @api_client.perform_vm_iso_detach(id)
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Vm]
      def perform_vm_metadata_set(id, payload)
        @api_client.perform_vm_metadata_set(id, payload)
      end
    end
  end
end
