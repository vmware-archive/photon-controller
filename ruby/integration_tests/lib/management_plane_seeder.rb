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

require_relative "../lib/test_helpers"

module EsxCloud
  class ManagementPlaneSeeder
    def self.populate()
      seeder = ManagementPlaneSeeder.new
      image_file = ENV["ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE"] || fail("ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE is not defined")
      image_id = EsxCloud::Image.create(image_file, random_name("image-"), "EAGER").id
      2.times do
        tenant = create_random_tenant
        2.times do
          resource_ticket = tenant.create_resource_ticket :name => random_name("rt-"), :limits => create_small_limits
          2.times do
            project = tenant.create_project name: random_name("project-"), resource_ticket_name: resource_ticket.name, limits: [create_limit("vm", 10.0, "COUNT"), create_limit("vm.memory", 50.0, "GB")]
            # create vms
            3.times do
              seeder.create_vm project, random_name("vm-"), image_id
            end
          end
        end
      end
    end

    def self.create_cluster
      seeder = ManagementPlaneSeeder.new
      tenant = create_random_tenant
      resource_ticket = tenant.create_resource_ticket :name => random_name("rt-"), :limits => create_small_limits
      project = tenant.create_project name: random_name("cluster-project-"), resource_ticket_name: resource_ticket.name, limits: [create_limit("vm", 10.0, "COUNT"), create_limit("vm.memory", 50.0, "GB")]

      project.create_cluster(
          name: random_name("kubernetes-"),
          type: "KUBERNETES",
          vm_flavor: seeder.create_vm_flavor.name,
          disk_flavor: seeder.create_ephemeral_disk_flavor.name,
          network_id: seeder.create_network([ENV["CLUSTER_NETWORK"]|| fail("CLUSTER_NETWORK not set")]).id,
          slave_count: 2,
          extended_properties: {
              "dns" => ENV["CLUSTER_DNS"] || fail("CLUSTER_DNS not set"),
              "gateway" => ENV["CLUSTER_GATEWAY"] || fail("CLUSTER_GATEWAY not set"),
              "netmask" => ENV["CLUSTER_NETMASK"] || fail("CLUSTER_NETMASK not set"),
              "master_ip" => ENV["CLUSTER_MASTER_IP"] || fail("CLUSTER_MASTER_IP not set"),
              "container_network" => "10.2.0.0/16"
          }
      )
    end

    def create_vm(project, vm_name, image_id)
      ephemeral_disk = create_ephemeral_disk random_name("#{vm_name}-disk-e-")
      persistent_disk = create_persistent_disk project, random_name("#{vm_name}-disk-")
      create_vm_spec = { image_id: image_id,
                         name: vm_name,
                         flavor: create_vm_flavor.name,
                         disks: [ephemeral_disk] }
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

    def create_network(networks)
      spec = EsxCloud::NetworkCreateSpec.new(random_name("network-"), "Cluster Network", networks)
      EsxCloud::Config.client.create_network(spec.to_hash)
    end
  end
end