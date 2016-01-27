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

require "spec_helper"

describe "VM lifecycle", life_cycle: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new(create_small_limits)
    @cleaner = EsxCloud::SystemCleaner.new(client)
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
  end

  context "single VM" do
    context 'without existing persistent disks' do
      it 'should exercise the vm lifecycle' do
        vm_lifecycle(@seeder.project!, @seeder.persistent_disk_flavor!)
      end
    end
  end

  context "multiple VMs simultaneously" do
    N_VMS = (ENV["N_VMS"] || 0).to_i

    it "should succeed creating 15 VMs simultaneously" do
      create_vms_simultaneously(15, @seeder.tenant!, @seeder.vm_flavor!, @seeder.persistent_disk_flavor!)
      puts "create 15 vms simultaneously completed"
    end

    unless N_VMS <= 0
      it "should succeed creating #{N_VMS} VMs simultaneously" do
        create_vms_simultaneously(N_VMS, @seeder.tenant!, @seeder.vm_flavor!, @seeder.persistent_disk_flavor!)
        puts "create #{N_VMS} vms simultaneously completed"
      end
    end
  end

  private

  def vm_lifecycle(project, disk_flavor)
    vm = create_vm(project)
    vm.state.should eq("STOPPED"), "VM #{vm.id} state was #{vm.state} instead of STOPPED"
    existing_persistent_disks = vm.get_attached_disk_names("persistent-disk")
    existing_persistent_disks.size.should eq(0), "VM #{vm.id} has #{existing_persistent_disks.size} disks instead of 0"

    disk = project.create_disk(
        name: random_name("disk-"),
        kind: "persistent-disk",
        flavor: disk_flavor.name,
        capacity_gb: 1,
        boot_disk: false,
        affinities: [{id: vm.id, kind: "vm"}])
    disk_id = disk.id
    disk_name = disk.name

    vm.attach_disk(disk_id)
    attached_disk_names = vm.get_attached_disk_names("persistent-disk")
    attached_disk_names.last.should eq(disk_name), "VM #{vm.id} failed to attach disk id #{disk_id}, name #{disk_name}"

    vm.detach_disk(disk_id)
    attached_disk_names = vm.get_attached_disk_names("persistent-disk")
    attached_disk_names.size.should eq(existing_persistent_disks.size), "VM #{vm.id} failed to detach disk id #{disk_id}, name #{disk_name}"

    vm.attach_disk(disk_id)
    vm.start!
    vm.state.should eq("STARTED"), "VM #{vm.id} state is #{vm.state} instead of STARTED"
    vm.stop!
    vm.state.should eq("STOPPED"), "VM #{vm.id} state is #{vm.state} instead of STOPPED"
    attached_disk_names = vm.get_attached_disk_names("persistent-disk")
    attached_disk_names.last.should eq(disk_name), "VM #{vm.id} does not have attached disk id #{disk_id}, name #{disk_name}"

    vm.detach_disk(disk_id)
    attached_disk_names = vm.get_attached_disk_names("persistent-disk")
    attached_disk_names.size.should eq(existing_persistent_disks.size), "VM #{vm.id} failed to detach disk id #{disk_id}, name #{disk_name}"

    vm.set_metadata({metadata: {"key" => "value"}})
    vm.get_metadata.should == {"key" => "value"}

    hosts_map = {}
    deployment = client.find_all_api_deployments.items.first
    hosts = client.get_deployment_hosts(deployment.id)
    hosts.items.each { |x| hosts_map[x.address] = x }
    if hosts_map.length != 0
      vm.host.should_not be_nil
      tags = hosts_map[vm.host].usage_tags
      tags.should_not be_nil
      expect(tags).not_to eq(["MGMT"])
    end

  ensure
    ignoring_all_errors { disk.delete if disk }
    ignoring_all_errors { vm.delete if vm }
  end

  def create_vms_simultaneously(n_vms, tenant, vm_flavor, disk_flavor)
    fail "Negative n_vms #{n_vms} is not allowed!" if n_vms < 0

    limit_vms = EsxCloud::QuotaLineItem.new("vm.flavor.#{vm_flavor.name}", n_vms, "COUNT")

    resource_ticket_name = random_name("ticket-")
    ticket = tenant.create_resource_ticket(name: resource_ticket_name, limits: [limit_vms])
    project = tenant.create_project(
        name: random_name("project-"),
        resource_ticket_name: resource_ticket_name,
        limits: [limit_vms]
    )

    success_count = 0
    vm_lock = Mutex.new
    threads = (1..n_vms).map do
      Thread.new do
        ignoring_api_errors do
          vm_lifecycle(project, disk_flavor)
          vm_lock.synchronize { success_count += 1 }
        end
      end
    end

    threads.each { |thr| thr.join }

    success_count.should == n_vms

    ticket = find_resource_ticket_by_id(ticket.id)
    ticket.usage.find { |qli| qli.key == "vm.flavor.#{vm_flavor.name}" }.value.should == n_vms
  end
end
