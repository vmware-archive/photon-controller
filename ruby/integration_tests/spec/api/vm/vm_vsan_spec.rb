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

describe "vm", management: true, vsan: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new(create_small_limits, [5.0])
    @cleaner = EsxCloud::SystemCleaner.new(client)
    @project = @seeder.project!
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
  end

  it "should create vm on vsan datastore when datastore tag is VSAN" do
    vm_name = random_name("vm-")
    network1 = EsxCloud::SystemSeeder.instance.network!
    edisk_flavor_vsan = @seeder.ephemeral_disk_flavor_with_vsan_tag!
    disks = [EsxCloud::VmDisk.new("#{vm_name}-disk1", "ephemeral-disk", edisk_flavor_vsan.name, nil, true)]

    vm = create_vm(@project, name: vm_name, disks: disks, networks: [network1.id])
    vm.name.should == vm_name

    datastore = client.find_datastore_by_id(vm.datastore)
    datastore.type.should == "VSAN"

    validate_vm_tasks(client.get_vm_tasks(vm.id))
    validate_vm_tasks(client.get_vm_tasks(vm.id, "COMPLETED"))
    vm.delete
  end

  it "should create vm on vmfs datastore when datastore tag is SHARED_VMFS" do
    vm_name = random_name("vm-")
    network1 = EsxCloud::SystemSeeder.instance.network!
    edisk_flavor_shared = @seeder.ephemeral_disk_flavor_with_shared_tag!
    disks = [EsxCloud::VmDisk.new("#{vm_name}-disk1", "ephemeral-disk", edisk_flavor_shared.name, nil, true)]

    vm = create_vm(@project, name: vm_name, disks: disks, networks: [network1.id])
    vm.name.should == vm_name

    datastore = client.find_datastore_by_id(vm.datastore)
    datastore.type.should == "SHARED_VMFS"

    validate_vm_tasks(client.get_vm_tasks(vm.id))
    validate_vm_tasks(client.get_vm_tasks(vm.id, "COMPLETED"))
    vm.delete
  end

  private

  def validate_vm_tasks(task_list)
    tasks = task_list.items
    isCreateTask = false
    tasks.each{ |task|
      if task.entity_kind == "vm" and task.operation == "CREATE_VM"
        isCreateTask = true
        break
      end
    }
    isCreateTask.should == true
  end
end