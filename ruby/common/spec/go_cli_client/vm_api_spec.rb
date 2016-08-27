# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

require_relative "../spec_helper"

describe EsxCloud::GoCliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    cmd = "/path/to/cli target set --nocertcheck localhost:9000"
    expect(EsxCloud::CmdRunner).to receive(:run).with(cmd)
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  }

  it "creates a VM" do
    tenant = double(EsxCloud::Tenant, name: "t1")
    project = double(EsxCloud::Project, id: "foo", name: "p1")
    client.project_to_tenant["foo"] = tenant

    vm_id = double("bar")
    vm = double(EsxCloud::Vm, id: vm_id)

    spec = {
        name: "vm1",
        flavor: "core-100",
        sourceImageId: "image-id",
        attachedDisks: [
            {name: "disk1", kind: "ephemeral", flavor: "core-100", bootDisk: true},
            {name: "disk2", kind: "ephemeral", flavor: "core-200", capacityGb: 10, bootDisk: false}
        ]
    }

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
                      .with("vm create -t 't1' -p 'p1' -n 'vm1' -f 'core-100' -i 'image-id' " +
                                       "-d 'disk1 core-100 boot=true, disk2 core-200 10'")
                      .and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).with(vm_id).and_return(vm)

    client.create_vm("foo", spec).should == vm
    client.vm_to_project[vm_id].should == project

  end

  it "creates a VM with affinities settings" do
    tenant = double(EsxCloud::Tenant, name: "t1")
    project = double(EsxCloud::Project, id: "foo", name: "p1")
    client.project_to_tenant["foo"] = tenant

    vm_id = double("bar")
    vm = double(EsxCloud::Vm, id: vm_id)



    spec = {
        name: "vm1",
        flavor: "core-100",
        sourceImageId: "image-id",
        attachedDisks: [
            {name: "disk1", kind: "ephemeral", flavor: "core-100", bootDisk: true},
            {name: "disk2", kind: "ephemeral", flavor: "core-200", capacityGb: 10, bootDisk: false}
        ],
        affinities: [
            {
                id: "disk-id1",
                kind: "disk",
            },
            {
                id: "disk-id2",
                kind: "disk",
            }
        ]
    }

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
                      .with("vm create -t 't1' -p 'p1' -n 'vm1' -f 'core-100' -i 'image-id' " +
                                       "-d 'disk1 core-100 boot=true, disk2 core-200 10' " +
                                       "-a 'disk:disk-id1, disk:disk-id2'")
                      .and_return(vm_id)

    expect(client).to receive(:find_vm_by_id).with(vm_id).and_return(vm)

    client.create_vm("foo", spec).should == vm
    client.vm_to_project[vm_id].should == project
  end

  it "deletes a VM" do
    expect(client).to receive(:run_cli).with("vm delete 'bar'")

    client.delete_vm("bar").should be_true
  end

  it "finds VM by id" do
    vm_id = double("vm1-id")
    vm_hash = { "id" => vm_id,
                "name" => "vm1",
                "state" => "STOPPED",
                "flavor" => "core-100",
                "sourceImageId" => "image-id",
                "host" => "10.146.36.28",
                "datastore" => "datastore1",
                "metadata" => {"a" => "b", "c" => "d"},
                "tags" => ["tag1", "tag2"],
                "attachedDisks" => [{ "id" => "disk1_id",
                                      "name" => "disk1",
                                      "kind" => "ephemeral-disk",
                                      "flavor" => "core-100",
                                      "capacityGb" => 2,
                                      "bootDisk" => true}],
                "attachedIsos" =>[{ "id" => "iso1_id",
                                    "name" => "iso1-name",
                                    "kind" => "iso",
                                    "size" => 10}]
              }

    vm = EsxCloud::Vm.create_from_hash(vm_hash)
    result = "vm1-id	vm1	STOPPED	core-100	image-id	10.146.36.28	datatstore1	a:b,c:d	tag1,tag2
              disk1_id	disk1	ephemeral-disk	core-100	2	true
              iso1_id	iso1-name	iso	10"

    expect(client).to receive(:run_cli).with("vm show #{vm_id}").and_return(result)
    expect(client).to receive(:get_vm_from_response).with(result).and_return(vm)
    expect(client.find_vm_by_id(vm_id)).to eq vm
  end

  it "starts VM " do
    vm_id = double("vm1-id")
    vm = double(EsxCloud::Vm, id: vm_id)

    expect(client).to receive(:run_cli).with("vm start '#{vm_id}'").and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.start_vm(vm_id).should == vm
  end

  it "stops VM " do
    vm_id = double("vm1-id")
    vm = double(EsxCloud::Vm, id: vm_id)

    expect(client).to receive(:run_cli).with("vm stop '#{vm_id}'").and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.stop_vm(vm_id).should == vm
  end

  it "restarts VM " do
    vm_id = double("vm1-id")
    vm = double(EsxCloud::Vm, id: vm_id)

    expect(client).to receive(:run_cli).with("vm restart '#{vm_id}'").and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.restart_vm(vm_id).should == vm
  end

  it "resumes VM " do
    vm_id = double("vm1-id")
    vm = double(EsxCloud::Vm, id: vm_id)

    expect(client).to receive(:run_cli).with("vm resume '#{vm_id}'").and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.resume_vm(vm_id).should == vm
  end

  it "suspends VM " do
    vm_id = double("vm1-id")
    vm = double(EsxCloud::Vm, id: vm_id)

    expect(client).to receive(:run_cli).with("vm suspend '#{vm_id}'").and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.suspend_vm(vm_id).should == vm
  end

  it "performs VM disk attach" do
    vm_id = double("vm1-id")
    disk_id = double("disk_id")
    vm = double(EsxCloud::Vm, :name => "vm_name")
    expect(client).to receive(:run_cli)
                      .with("vm attach-disk '#{vm_id}' -d '#{disk_id}'")
                      .and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.perform_vm_disk_operation(vm_id, "ATTACH_DISK", disk_id).should == vm
  end

  it "performs VM disk detach" do
    vm_id = double("vm1-id")
    vm = double(EsxCloud::Vm, :name => "vm_name")
    disk_id = double("disk_id")

    expect(client).to receive(:run_cli)
                      .with("vm detach-disk '#{vm_id}' -d '#{disk_id}'")
                      .and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.perform_vm_disk_operation(vm_id, "DETACH_DISK", disk_id).should == vm
  end

  it "detaches an ISO" do
    vm_id = double("vm-id")
    vm = double(EsxCloud::Vm, id: vm_id)

    expect(client).to receive(:run_cli)
                      .with("vm detach-iso '#{vm_id}'")
                      .and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.perform_vm_iso_detach(vm_id).should == vm
  end

  it "sets VM metadata" do
    vm_id = double("vm-id")
    vm = double(EsxCloud::Vm, id: vm_id, metadata: '{"key":"value"}')

    expect(client).to receive(:run_cli)
                      .with("vm set-metadata '#{vm_id}' -m '{\"key\":\"value\"}'")
                      .and_return(vm_id)
    expect(client).to receive(:find_vm_by_id).once.with(vm_id).and_return(vm)

    client.perform_vm_metadata_set(vm_id, metadata: '{"key":"value"}').should == vm
  end

  it "gets vm tasks" do
    vm_id = double("bar")
    result = "task1 COMPLETED CREATE_TENANT  1458853080000  1000
              task2 COMPLETED DELETE_TENANT  1458853089000  1000"
    tasks = double(EsxCloud::TaskList)
    expect(client).to receive(:run_cli).with("vm tasks '#{vm_id}' -s 'COMPLETED'").and_return(result)
    expect(client).to receive(:get_task_list_from_response).with(result).and_return(tasks)
    client.get_vm_tasks(vm_id, "COMPLETED").should == tasks
  end

  it "finds VMs by name" do
    tenant = double(EsxCloud::Tenant, name: "t1")
    project = double(EsxCloud::Project, id: "id", name: "p1")
    client.project_to_tenant[project.id] = tenant

    result ="vmId1 vm1 STARTED"
    vms = double(EsxCloud::VmList)

    expect(client).to receive(:find_project_by_id).with(project.id).and_return(project)
    expect(client).to receive(:run_cli).with("vm list -t 't1' -p 'p1' -n 'vm1'").and_return(result)
    expect(client).to receive(:get_vm_list_from_response).with(result).and_return(vms)

    expect(client.find_vms_by_name(project.id, "vm1")).to eq vms
  end
end
