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

require_relative "test_helpers"
require_relative "../lib/integration"
require_relative "../spec/support/api_client_helper"

include EsxCloud::TestHelpers

module EsxCloud
  class UptimeHelper

    class << self

      def power_off_management_vms(instances)
        return if instances.nil? or instances.to_i <= 0

        EsxCloud::Config.init
        EsxCloud::Config.client = ApiClientHelper.management
        deployments = EsxCloud::Deployment.find_all
        management_vms = EsxCloud::Config.client.get_deployment_vms deployments.items[0].id
        count = 0
        EsxCloud::Config.client = ApiClientHelper.management(port: 9000)
        management_vms.items.each do |management_vm|
          unless management_vm.metadata.has_value?("LoadBalancer")
            puts "Powering off management VM " + management_vm.id
            ignoring_all_errors { management_vm.stop! }
            count += 1
            break if count == instances.to_i
          end
        end
      end

      def power_on_management_vms
        EsxCloud::Config.init
        EsxCloud::Config.client = ApiClientHelper.management
        deployments = EsxCloud::Deployment.find_all
        management_vms = EsxCloud::Config.client.get_deployment_vms deployments.items[0].id
        management_vms.items.each do |management_vm|
          if management_vm.state == "STOPPED"
            puts "Powering on management VM " + management_vm.id
            management_vm.start!
            # Wait for the VM to come up and get an ip address
            sleep 120

            vm_networks = EsxCloud::Config.client.get_vm_networks(management_vm.id)
            vm_networks.network_connections.each do |network|
              if network.network == "VM VLAN"
                EsxCloud::HostCleaner.clean_xenon_state network.ip_address, "esxcloud", "vmware"
              end
            end
          end
        end
      end

      def stop_agent(dc_yml, usage_tags)
        host_specs = EsxCloud::HostsImporter.import_file dc_yml
        usage_tags = usage_tags.split(" ")
        host_specs.each do |host|
          if host.usage_tags.sort == usage_tags.sort
            EsxCloud::HostCleaner.stop_agent host.address, host.username, host.password
            break
          end
        end
      end

      def start_agent(dc_yml, usage_tags)
        host_specs = EsxCloud::HostsImporter.import_file dc_yml
        usage_tags = usage_tags.split(" ")
        host_specs.each do |host|
          if host.usage_tags.sort == usage_tags.sort
            EsxCloud::HostCleaner.start_agent host.address, host.username, host.password
            break
          end
        end
      end
    end
  end
end
