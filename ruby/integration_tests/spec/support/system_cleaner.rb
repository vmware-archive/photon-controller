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
  class SystemCleaner

    attr_accessor :client

    # @param [ApiClient, CliClient, or GoCliClient] client
    # @param [Boolean] for
    def initialize(client)
      @client = client
    end

    # @param [SystemSeeder]
    def clean_images(seeder)
      seeder.bootable_image.delete if seeder.bootable_image
      seeder.image.delete if seeder.image
      seeder.error_image.delete if seeder.error_image
      seeder.vm_for_pending_delete_image.delete if seeder.pending_delete_image
    end

    # @return [Hash] stat
    def clean_system
      stat = {}
      client.find_all_tenants.items.each do |tenant|
        delete_tenant tenant, stat
      end

      client.find_all_images.items.each do |image|
        delete_image image, stat
      end

      # In physical network situation, all the networks need to be cleaned up
      # at system level
      deployment = client.find_all_api_deployments.items.first
      unless deployment.network_configuration.virtual_network_enabled
        client.find_all_networks.items.each do |network|
          delete_network network, stat
        end
      end

      clean_flavors
      stat
    end

    # @param [Hash] stat
    # @return [Hash] stat
    def clean_flavors(stat = {})
      client.find_all_flavors.items.each do |flavor|
        delete_flavor flavor, stat
      end
      stat
    end

    # @param [Flavor] flavor
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_flavor(flavor, stat = {})
      fail "delete_flavor: flavor is nil!" unless flavor

      update_stat stat, "flavor", flavor.id
      flavor.delete
      stat
    end

    # @param [Hash] stat
    # @return [Hash] stat
    def clean_hosts(stat = {})
      deployment = client.find_all_api_deployments.items.first
      client.get_deployment_hosts(deployment.id).items.each do |host|
        delete_host host, stat
      end
      stat
    end

    # @param [Host] host
    # @param [Hash] stat
    # @return [Hash]
    def delete_host(host, stat = {})
      fail "delete_host: host is nil!" unless host

      update_stat stat, "host", host.id
      client.mgmt_delete_host(host.id)
      stat
    end

    # @param [Image] image
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_image(image, stat = {})
      fail "delete_image: image is nil!" unless image

      update_stat stat, "image", image.id
      image.delete
      stat
    end

    # @param [Network] network
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_network(network, stat = {})
      fail "delete network: network is nil!" unless network

      update_stat stat, "network", network.id
      network.delete
      stat
    end

    # @param [Tenant] tenant
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_tenant(tenant, stat = {})
      fail "delete_tenant: tenant is nil!" unless tenant
      client.find_all_projects(tenant.id).items.each do |project|
        delete_project(project, stat)
      end

      update_stat stat, "tenant", tenant.id
      tenant.delete
      stat
    end

    # @param [Project] project
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_project(project, stat = {})
      fail "delete_project: project is nil!" unless project
      # need to delete disk first before delete vm to detach disk
      client.find_all_disks(project.id).items.flatten.each do |disk|
        delete_disk(disk, stat)
      end

      client.find_all_vms(project.id).items.flatten.each do |vm|
        delete_vm(vm, stat)
      end

      # Delete virtual networks under this project
      deployment = client.find_all_api_deployments.items.first
      if deployment.network_configuration.virtual_network_enabled
        client.get_project_networks(project.id).items.flatten.each do |network|
          delete_network(network, stat)
        end
      end

      update_stat stat, "project", project.id
      project.delete
      stat
    end

    # @param [Disk] disk
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_disk(disk, stat = {})
      fail "delete_disk: disk is nil!" unless disk
      disk.vms.each do |vm_id|
        update_stat stat, vm, vm.id
        update_stat stat, "disk", disk.id
        client.find_vm_by_id(vm_id).detach_disk(disk.id)
      end

      update_stat stat, "disk", disk.id
      disk.delete
      stat
    end

    # @param [Vm] vm
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_vm(vm, stat = {})
      fail "delete_vm: vm is nil!" unless vm
      vm.stop! if vm.state == "STARTED"
      update_stat stat, "vm", vm.id
      vm.delete
      stat
    end

    # @param [Deployment] deployment
    # @param [Hash] stat
    # @return [Hash] stat
    def delete_deployment(deployment, stat = {})
      fail "delete_deployment: deployment is nil!" unless deployment

      update_stat stat, "deployment", deployment.id
      deployment.delete
      stat
    end

    private

    # @param [Hash] stat
    # @param [String] key
    # @param [Object] new_entry, typically entity id
    def update_stat(stat, key, new_entry)
      stat[key] ||= []
      stat[key] << new_entry
    end
  end
end
