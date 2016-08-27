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

require "json"

module EsxCloud
  class GoCliClient
    module HostApi

      # @param [String] deployment_id
      # @param [Hash] payload
      # @return [Host]
      def create_host(deployment_id, payload)
        cmd = "host create -u '#{payload[:username]}'"
        cmd += " -p '#{payload[:password]}'"
        cmd += " -i '#{payload[:address]}'"
        cmd += " -z '#{payload[:availabilityZone]}'" if payload[:availabilityZone]
        cmd += " -t '#{payload[:usageTags].join(",")}'" if payload[:usageTags]
        cmd += " -m '#{payload[:metadata].to_json}'" if payload[:metadata]
        cmd += " -d '#{deployment_id}'"

        host_id = run_cli(cmd)
        mgmt_find_host_by_id(host_id)
      end

      # @return [HostList]
      def find_all_hosts
        @api_client.find_all_hosts
      end

      # @param [String] id
      # @return [Host]
      def find_host_by_id(id, property=nil, subId=nil)
        @api_client.find_host_by_id(id, property, subId)
      end

      # @param [String] id
      # @return [Host]
      def mgmt_find_host_by_id(id)
        cmd = "host show #{id}"
        result = run_cli(cmd)
        get_host_from_response(result)
      end

      # @param [String] id
      # @param [String] storeName
      # @param [String] storeId
      # @return [Store]
      def create_host_store(id, storeName, storeId)
        @api_client.create_host_store(id, storeName, storeId)
      end

      # @param [String] id
      # @param [String] storeName
      # @return [Store]
      def delete_host_store(id, storeName)
        @api_client.delete_host_store(id, storeName)
      end

      # @param [String] id
      # @param [String] property
      # @param [String] value
      # @return [Metadata]
      def update_host_property(id, property, value)
        @api_client.update_host_property(id, property, value)
      end

      # @param [String] id
      # @return [Metadata]
      def find_host_metadata_by_id(id)
        @api_client.find_host_metadata_by_id(id)
      end

      # @param [String] id
      # @return [TaskList]
      def find_tasks_by_host_id(id)
        cmd = "host tasks '#{id}'"

        result = run_cli(cmd)
        get_task_list_from_response(result)
      end

      # @param [String] id
      # @param [String] property
      # @param [String] value
      # @return [Metadata]
      def update_host_metadata_property(id, property, value)
        @api_client.update_host_metadata_property(id, property, value)
      end

      # @param [String] id
      def mgmt_delete_host(id)
        run_cli("host delete '#{id}'")
        true
      end

      # @param [String] id
      # @return [VmList]
      def mgmt_get_host_vms(id)
        result = run_cli ("host list-vms '#{id}'")
        get_vm_list_from_response(result)
      end

      # @param [String] id
      # @return [Host]
      def host_enter_maintenance_mode(id)
        host_id = run_cli("host enter-maintenance '#{id}'")
        mgmt_find_host_by_id(host_id)
      end

      # @param [String] id
      # @return [Host]
      def host_enter_suspended_mode(id)
        host_id = run_cli("host suspend '#{id}'")
        mgmt_find_host_by_id(host_id)
      end

      # @param [String] id
      # @return [Host]
      def host_exit_maintenance_mode(id)
        host_id = run_cli("host exit-maintenance '#{id}'")
        mgmt_find_host_by_id(host_id)
      end

      # @param [String] id
      # @return [Host]
      def host_resume(id)
        host_id = run_cli("host resume '#{id}'")
        mgmt_find_host_by_id(host_id)
      end

      # @param [String] host_id
      # @param [Hash] payload
      # @return [Host]
      def host_set_availability_zone(host_id, payload)
        host_id = run_cli("host set-availability-zone '#{host_id}' '#{payload[:availability_zone]}'")
        mgmt_find_host_by_id(host_id)
      end

      private

      # @param [String] result
      # @return [Host]
      def get_host_from_response(result)
        result.slice! "\n"
        values = result.split("\t", -1)
        host_hash = Hash.new
        host_hash["id"]               = values[0] unless values[0] == ""
        host_hash["username"]         = values[1] unless values[1] == ""
        host_hash["password"]         = values[2] unless values[2] == ""
        host_hash["address"]          = values[3] unless values[3] == ""
        host_hash["usageTags"]        = values[4] unless values[4] == ""
        host_hash["state"]            = values[5] unless values[5] == ""
        host_hash["metadata"]         = metadata_to_hash(values[6])
        host_hash["availabilityZone"] = values[7] unless values[7] == ""
        host_hash["esxVersion"]       = values[8] unless values[8] == ""

        Host.create_from_hash(host_hash)
      end

    end
  end
end
