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
    module VmApi

      VMS_ROOT = "/vms"

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Vm]
      def create_vm(project_id, payload)
        response = @http_client.post_json("/projects/#{project_id}/vms", payload)
        check_response("Create VM (#{project_id}) #{payload}", response, 201)

        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_vm(id)
        response = @http_client.delete("#{VMS_ROOT}/#{id}")
        check_response("Delete VM '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] project_id
      # @return [VmList]
      def find_all_vms(project_id)
        response = @http_client.get("/projects/#{project_id}/vms")
        check_response("Find all VMs for project '#{project_id}'", response, 200)

        VmList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Vm]
      def find_vm_by_id(id)
        response = @http_client.get("#{VMS_ROOT}/#{id}")
        check_response("Find VM by ID '#{id}'", response, 200)

        Vm.create_from_json(response.body)
      end

      # @param [String] project_id
      # @param [String] name
      # @return [VmList]
      def find_vms_by_name(project_id, name)
        response = @http_client.get("/projects/#{project_id}/vms?name=#{name}")
        check_response("Find VMs by name '#{name}' in project '#{project_id}'", response, 200)

        VmList.create_from_json(response.body)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_vm_tasks(id, state = nil)
        url = "#{VMS_ROOT}/#{id}/tasks"
        url += "?state=#{state}" if state
        response = @http_client.get(url)
        check_response("Get tasks for VM '#{id}'", response, 200)

        TaskList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [VmNetworks]
      def get_vm_networks(id)
        url = "#{VMS_ROOT}/#{id}/networks"
        response = @http_client.get(url)
        check_response("Get network connections for VM '#{id}'", response, 201)

        task = poll_response(response)
        VmNetworks.create_from_task(task)
      end

      # @param [String] id
      # @return [MksTicket]
      def get_vm_mks_ticket(id)
        url = "#{VMS_ROOT}/#{id}/mks_ticket"
        response = @http_client.get(url)
        check_response("Get mks ticket for VM '#{id}'", response, 201)

        task = poll_response(response)
        MksTicket.create_from_hash(task.resource_properties)
      end

      # @param [String] id
      # @return [Vm]
      def start_vm(id)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/start", {})
        check_response("Perform start operation on VM '#{id}'", response, 201)
        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Vm]
      def stop_vm(id)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/stop", {})
        check_response("Perform stop operation on VM '#{id}'", response, 201)
        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Vm]
      def restart_vm(id)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/restart", {})
        check_response("Perform restart operation on VM '#{id}'", response, 201)
        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Vm]
      def resume_vm(id)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/resume", {})
        check_response("Perform resume operation on VM '#{id}'", response, 201)
        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Vm]
      def suspend_vm(id)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/suspend", {})
        check_response("Perform suspend operation on VM '#{id}'", response, 201)
        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @param [String] operation
      # @param [Hash] args
      # @return [Vm]
      def perform_vm_disk_operation(id, operation, disk_id, args = {})
        op = operation.to_s.downcase
        payload = {diskId: disk_id, arguments: args}
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/#{op}", payload)
        check_response("Perform VM disk operation #{op} #{payload} on VM '#{id}'", response, 201)

        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @param [String] iso_path
      # @param [String] iso_name
      # @return [Vm]
      def perform_vm_iso_attach(id, iso_path, iso_name = nil)
        response = @http_client.upload("#{VMS_ROOT}/#{id}/attach_iso", iso_path, iso_name)
        check_response("Attach ISO '#{iso_path}' to VM", response, 201)

        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Vm]
      def perform_vm_iso_detach(id)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/detach_iso", nil)
        check_response("Detach ISO to VM #{id}", response, 201)

        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Vm]
      def perform_vm_metadata_set(id, payload)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/set_metadata", payload)
        check_response("Set Metadata to VM #{id}", response, 201)

        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Vm]
      def acquire_floating_ip(id, payload)
        response = @http_client.post_json("#{VMS_ROOT}/#{id}/acquire_floating_ip", payload)
        check_response("Acquire floating IP for VM #{id}", response, 201)

        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end

      # @param [Stirng] id
      # @return [Vm]
      def release_floating_ip(id)
        response = @http_client.delete("#{VMS_ROOT}/#{id}/release_floating_ip")
        check_response("Release floating IP from VM #{id}", response, 201)

        task = poll_response(response)
        find_vm_by_id(task.entity_id)
      end
    end
  end
end
