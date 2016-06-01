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

require_relative 'test_helpers'

module EsxCloud
  class ManagementPlaneSeeder
    def self.populate()
      seeder = ManagementPlaneSeeder.new
      image_file = ENV["ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE"] || fail("ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE is not defined")
      image_id = EsxCloud::Image.create(image_file, seeder.random_name("image-"), "EAGER").id
      network = seeder.create_network
      network2 = seeder.create_network2
      availibility_zone = seeder.create_availablity_zone
      2.times do
        tenant = seeder.create_random_tenant
        2.times do
          resource_ticket = tenant.create_resource_ticket :name => seeder.random_name("rt-"), :limits => seeder.create_small_limits
          2.times do
            project = tenant.create_project name: seeder.random_name("project-"), resource_ticket_name: resource_ticket.name, limits: [seeder.create_limit("vm", 10.0, "COUNT"), seeder.create_limit("vm.memory", 50.0, "GB")]
            # create vms
            3.times do
              seeder.create_vm project, seeder.random_name("vm-"), image_id, network
            end
          end
        end
      end
    end

    def create_vm(project, vm_name, image_id, network)
      ephemeral_disk = create_ephemeral_disk(random_name("#{vm_name}-disk-e-"))
      persistent_disk = create_persistent_disk(project, random_name("#{vm_name}-disk-"))
      create_vm_spec = { image_id: image_id,
                         name: vm_name,
                         flavor: create_vm_flavor.name,
                         disks: [ephemeral_disk],
                         networks: [network.id] }
      vm = project.create_vm create_vm_spec
      vm.attach_disk(persistent_disk.id)
      vm
    end

    def create_ephemeral_disk(name)
      flavor_name = create_ephemeral_disk_flavor.name
      EsxCloud::VmDisk.new(name, "ephemeral-disk", flavor_name, nil, true)
    end

    def create_persistent_disk(project, name)
      flavor_name = create_persistent_disk_flavor.name
      #EsxCloud::VmDisk.new(name, "persistent-disk", flavor_name, 1, false)
      project.create_disk :name => name, :kind => "persistent-disk", :flavor => flavor_name, :capacity_gb => 1
    end

    def create_vm_flavor(cpu = 1.0, memory = 2.0, cost = 1.0)
      cost = [
          EsxCloud::QuotaLineItem.new("vm.cpu", cpu,"COUNT"),
          EsxCloud::QuotaLineItem.new("vm.memory", memory, "GB"),
          EsxCloud::QuotaLineItem.new("vm.cost", cost, "COUNT"),
      ]
      create_flavor "vm", cost
    end

    def create_random_tenant
      create_tenant(name: random_name("tenant-"))
    end

    def create_tenant(options = {})
      Tenant.create(TenantCreateSpec.new(options[:name], options[:security_groups]))
    end

    def create_ephemeral_disk_flavor(cost = 1.0)
      cost = [
          EsxCloud::QuotaLineItem.new("ephemeral-disk.cost", cost, "COUNT")
      ]

      create_flavor "ephemeral-disk", cost
    end

    def create_persistent_disk_flavor(cost = 1.0)
      cost = [
          EsxCloud::QuotaLineItem.new("persistent-disk.cost", cost, "COUNT")
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

    def create_network
      portgroup = get_port_group
      network_spec = EsxCloud::NetworkCreateSpec.new(random_name("network-"), "VLAN", [portgroup])
      EsxCloud::Network.create(network_spec)
    end

    def create_network2
      portgroup2 = get_port_group2
      network_spec2 = EsxCloud::NetworkCreateSpec.new(random_name("network-"), "VLAN", [portgroup2])
      EsxCloud::Network.create(network_spec2)
    end

    def create_availablity_zone
      availability_zone_spec = EsxCloud::AvailabilityZoneCreateSpec.new(random_name("availability-zone-"))
      EsxCloud::AvailabilityZone.create(availability_zone_spec)
    end

    def get_port_group
      ENV["ESX_VM_PORT_GROUP"] || "VM Network"
    end

    def get_port_group2
      ENV["ESX_VM_PORT_GROUP2"] || "VM Network2"
    end

    def create_small_limits
      [
          QuotaLineItem.new("vm.memory", 375.0, "GB"),
          QuotaLineItem.new("vm", 1000.0, "COUNT")
      ]
    end

    def create_limit(key, value, unit)
      QuotaLineItem.new(key, value, unit)
    end

    def random_name(prefix = "rn")
      prefix + SecureRandom.base64(12).tr("+/", "ab")
    end
  end
end
