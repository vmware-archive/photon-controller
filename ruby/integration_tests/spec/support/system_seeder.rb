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

require "securerandom"

module EsxCloud
  class SystemSeeder
    include TestHelpers

    def self.instance
      @@seeder ||= EsxCloud::SystemSeeder.new(create_limits)
    end

    attr_reader :vm_flavor, :huge_vm_flavor, :ephemeral_disk_flavor, :persistent_disk_flavor,
      :image, :bootable_image, :pending_delete_image, :error_image,
      :tenant, :resource_ticket, :project,
      :host, :vm, :vm_for_pending_delete_image,
      :persistent_disk, :network

    # @param [Array] resource_ticket_limits
    # @param [Array] subdivide_limits_percent
    def initialize(resource_ticket_limits, subdivide_limits_percent = [100])
      @resource_ticket_limits = resource_ticket_limits
      @subdivide_limits_percent = subdivide_limits_percent
    end

    def vm_flavor!
      @vm_flavor ||= create_vm_flavor
    end

    def huge_vm_flavor!
      @huge_vm_flavor ||= create_vm_flavor 8000.0, 9000.0, 9000.0
    end

    def ephemeral_disk_flavor!
      @ephemeral_disk_flavor ||= create_ephemeral_disk_flavor
    end

    def ephemeral_disk_flavor_with_local_tag!
      @ephemeral_disk_flavor_with_local_tag ||= create_ephemeral_disk_flavor_with_local_tag
    end

    def ephemeral_disk_flavor_with_shared_tag!
      @ephemeral_disk_flavor_with_shared_tag ||= create_ephemeral_disk_flavor_with_shared_tag
    end

    def persistent_disk_flavor!
      @persistent_disk_flavor ||= create_persistent_disk_flavor
    end

    def persistent_disk_flavor_with_local_tag!
      @persistent_disk_flavor_with_local_tag ||= create_persistent_disk_flavor_with_local_tag
    end

    def persistent_disk_flavor_with_shared_tag!
      @persistent_disk_flavor_with_shared_tag ||= create_persistent_disk_flavor_with_shared_tag
    end

    def image!
      @image ||= create_image
    end

    def bootable_image!
      @bootable_image ||= create_bootable_image
    end

    def pending_delete_image!
      @pending_delete_image ||= create_pending_delete_image
    end

    def pending_delete_vm_flavor!
      @pending_delete_flavor ||= create_pending_delete_flavor
    end

    def pending_delete_disk_flavor!
      @pending_delete_flavor ||= create_pending_delete_disk_flavor
    end

    def error_image!
      @error_image ||= create_error_image
    end

    def tenant!
      @tenant ||= create_random_tenant
    end

    def pending_delete_availability_zone!
      @pending_delete_availability_zone ||= create_pending_delete_availability_zone
    end

    def resource_ticket!
      @resource_ticket ||= tenant!.create_resource_ticket(
        name: random_name("rt-"),
        limits: @resource_ticket_limits)
    end

    def project!
      @project ||= tenant!.create_project(
        name: random_name("project-"),
        resource_ticket_name: resource_ticket!.name,
        limits: @subdivide_limits_percent.map do |percent|
          create_subdivide_limit(percent)
        end
      )
    end

    def vm!
      @vm ||= create_vm(project!)
    end

    def host!
      @host ||= create_host(deployment!, vm!)
    end

    def network
      @network ||= client.find_all_networks.items.find { |n| n.is_default }
    rescue
      nil
    end

    def network!
      network || @network = create_network
    end

    def persistent_disk!
      @persistent_disk ||= project!.create_disk(
        name: random_name("disk-"),
        kind: "persistent-disk",
        flavor: persistent_disk_flavor!.name,
        capacity_gb: 2,
        boot_disk: false,
        affinities: [{id: vm!.id, kind: "vm"}])
    end

    def deployment
      client.find_all_api_deployments.items[0]
    rescue
      nil
    end

    def deployment!
      deployment || create_deployment
    end

    def create_dummy_image_file
      dummy_file = File.expand_path(SecureRandom.hex, Dir.pwd)
      File.open(dummy_file, "w") do |f|
        f.write("dummy")
      end
      dummy_file
    end

    private

    def self.create_limits
      [
        QuotaLineItem.new("vm.memory", 15000.0, "GB"),
        QuotaLineItem.new("vm", 1000.0, "COUNT")
      ]
    end

    def create_vm_flavor(cpu = 1.0, memory = 2.0, cost = 1.0)
      cost = [
        EsxCloud::QuotaLineItem.new("vm.cpu", cpu,"COUNT"),
        EsxCloud::QuotaLineItem.new("vm.memory", memory, "GB"),
        EsxCloud::QuotaLineItem.new("vm.cost", cost, "COUNT"),
      ]

      create_flavor "vm", cost
    end

    def create_ephemeral_disk_flavor(cost = 1.0)
      cost = [
        EsxCloud::QuotaLineItem.new("ephemeral-disk.cost", cost, "COUNT")
      ]

      create_flavor "ephemeral-disk", cost
    end

    def create_ephemeral_disk_flavor_with_local_tag()
      cost = [
          EsxCloud::QuotaLineItem.new("storage.LOCAL_VMFS", 1.0, "COUNT")
      ]

      create_flavor "ephemeral-disk", cost
    end

    def create_ephemeral_disk_flavor_with_shared_tag()
      cost = [
          EsxCloud::QuotaLineItem.new("storage.SHARED_VMFS", 1.0, "COUNT")
      ]

      create_flavor "ephemeral-disk", cost
    end

    def create_persistent_disk_flavor(cost = 1.0)
      cost = [
        EsxCloud::QuotaLineItem.new("persistent-disk.cost", cost, "COUNT")
      ]

      create_flavor "persistent-disk", cost
    end

    def create_persistent_disk_flavor_with_local_tag()
      cost = [
          EsxCloud::QuotaLineItem.new("storage.LOCAL_VMFS", 1.0, "COUNT")
      ]

      create_flavor "persistent-disk", cost
    end

    def create_persistent_disk_flavor_with_shared_tag()
      cost = [
          EsxCloud::QuotaLineItem.new("storage.SHARED_VMFS", 1.0, "COUNT")
      ]

      create_flavor "persistent-disk", cost
    end

    def create_flavor(kind, cost = [])
      name = random_name "flavor-"
      cost << EsxCloud::QuotaLineItem.new(kind, 1.0, "COUNT")
      cost << EsxCloud::QuotaLineItem.new("#{kind}.flavor.#{name}", 1.0, "COUNT")

      spec = EsxCloud::FlavorCreateSpec.new name, kind, cost
      EsxCloud::Flavor.create spec
    end

    def create_availability_zone()
      name = random_name "avialability-zone-"
      spec = EsxCloud::AvailabilityZoneCreateSpec.new name
      EsxCloud::AvailabilityZone.create spec
    end

    def create_bootable_image
      image_file = ENV["ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE"] || fail("ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE is not defined")
      EsxCloud::Image.create(image_file, random_name("image-"), "EAGER")
    end

    def create_image
      image_file = ENV["ESXCLOUD_DISK_OVA_IMAGE"] || fail("ESXCLOUD_DISK_OVA_IMAGE is not defined")
      EsxCloud::Image.create(image_file, random_name("image-"), "EAGER")
    end

    def create_pending_delete_image
      image = create_image
      @vm_for_pending_delete_image = project!.create_vm(
        name: random_name("vm-"), flavor: vm_flavor!.name,
        image_id: image.id, disks: create_ephemeral_disks([random_name("disk-")]))
      image.delete
      EsxCloud::Image.find_by_id(image.id)
    end

    def create_pending_delete_vm_flavor
      flavor = create_vm_flavor
      @vm_for_pending_delete_flavor = project!.create_vm(
          name: random_name("vm-"), flavor: flavor.name,
          image_id: EsxCloud::SystemSeeder.instance.image!.id,
          disks: create_ephemeral_disks([random_name("disk-")]))
      flavor.delete
      EsxCloud::Flavor.find_by_id(flavor.id)
    end

    def create_pending_delete_disk_flavor
      disk_flavor = create_persistent_disk_flavor
      @disk_for_pending_delete_flavor = project!.create_disk(
          name: random_name("disk-"),
          kind: "persistent-disk",
          flavor: disk_flavor.name,
          capacity_gb: 2,
          boot_disk: false)
      disk_flavor.delete
      EsxCloud::Flavor.find_by_id(disk_flavor.id)
    end

    def create_error_image
      image_name = random_name("image-")
      dummy_file = create_dummy_image_file
      begin
        EsxCloud::Image.create(dummy_file, image_name, "EAGER")
      rescue EsxCloud::ApiError, EsxCloud::CliError
        File.delete(dummy_file) if File.exist?(dummy_file)
        EsxCloud::Image.find_all.items.find { |i| i.name == image_name }
      end
    end

    def create_pending_delete_availability_zone
      availability_zone = create_availability_zone
      availability_zone.delete
      EsxCloud::AvailabilityZone.find_by_id(availability_zone.id)
    end

    def create_network
      unless deployment.network_configuration.sdn_enabled then
        spec = EsxCloud::NetworkCreateSpec.new(random_name("network-"), "Seeder Network", [get_vm_port_group])
        network = EsxCloud::Config.client.create_network(spec.to_hash)
      else
        spec = EsxCloud::VirtualNetworkCreateSpec.new(random_name("network-"), "Seeder Virtual Network", "ROUTED", 128, 16)
        network = EsxCloud::VirtualNetwork.create(project!.id, spec)
      end
      EsxCloud::Config.client.set_default(network.id)
      network
    end

    def create_deployment
      spec = EsxCloud::DeploymentCreateSpec.new(["image_datastore"],
                                                EsxCloud::AuthConfigurationSpec.new(true, "t", "p", ["group"]),
                                                EsxCloud::NetworkConfigurationSpec.new(false),
                                                EsxCloud::StatsInfo.new(false),
                                                "0.0.0.1",
                                                "0.0.0.2",
                                                true)
      client.create_api_deployment(spec.to_hash)
    end
  end
end
