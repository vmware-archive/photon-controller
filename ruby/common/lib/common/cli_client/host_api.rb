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
  class CliClient
    module HostApi

      # @param [String] deployment_id
      # @param [Hash] payload
      # @return [Host]
      def create_host(deployment_id, payload)
        cmd = "host create -u '#{payload[:username]}'"
        cmd += " -t '#{payload[:password]}'"
        cmd += " -i '#{payload[:address]}'"
        cmd += " -z '#{payload[:availabilityZone]}'" if payload[:availabilityZone]
        cmd += " -t '#{payload[:usageTags].join(",")}'" if payload[:usageTags]
        cmd += " -m '#{payload[:metadata].to_json}'" if payload[:metadata]

        run_cli(cmd)

        hosts = get_deployment_hosts(deployment_id).items.select { |h| h.address == payload[:address] }
        if hosts.size > 1
          fail EsxCloud::CliError, "There are more than one Hosts."
        end
        hosts.first
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
        @api_client.mgmt_find_host_by_id(id)
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
        @api_client.find_tasks_by_host_id(id)
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
        @api_client.mgmt_delete_host(id)
      end

      # @param [String] id
      # @return [VmList]
      def mgmt_get_host_vms(id)
        @api_client.mgmt_get_host_vms(id)
      end

      # @param [String] id
      # @return [Host]
      def host_enter_maintenance_mode(id)
        @api_client.host_enter_maintenance_mode(id)
      end

      # @param [String] id
      # @return [Host]
      def host_enter_suspended_mode(id)
        @api_client.host_enter_suspended_mode(id)
      end

      # @param [String] id
      # @return [Host]
      def host_exit_maintenance_mode(id)
        @api_client.host_exit_maintenance_mode(id)
      end

      # @param [String] id
      # @return [Host]
      def host_resume(id)
        @api_client.host_resume(id)
      end

      # @param [String] host_id
      # @param [Hash] payload
      # @return [Host]
      def host_set_availability_zone(host_id, payload)
        @api_client.host_set_availability_zone(host_id, payload)
      end
    end
  end
end
