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

require_relative "../spec_helper"

describe EsxCloud::CliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    EsxCloud::CliClient.new("/path/to/cli", "localhost:9000")
  }

  it "creates a VM" do
    tenant = double(EsxCloud::Tenant, name: "t1")
    project = double(EsxCloud::Project, id: "foo", name: "p1")
    client.project_to_tenant["foo"] = tenant

    vm = double(EsxCloud::Vm, id: "bar")
    vms = double(EsxCloud::VmList, items: [vm])

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

    expect(client).to receive(:find_vms_by_name).with("foo", "vm1").and_return(vms)

    client.create_vm("foo", spec).should == vm
    client.vm_to_project["bar"].should == project

  end

  it "creates a VM with affinities settings" do
    tenant = double(EsxCloud::Tenant, name: "t1")
    project = double(EsxCloud::Project, id: "foo", name: "p1")
    client.project_to_tenant["foo"] = tenant

    vm = double(EsxCloud::Vm, id: "bar")
    vms = double(EsxCloud::VmList, items: [vm])

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
                                "-a 'disk disk-id1, disk disk-id2'")

    expect(client).to receive(:find_vms_by_name).with("foo", "vm1").and_return(vms)

    client.create_vm("foo", spec).should == vm
    client.vm_to_project["bar"].should == project
  end

  it "deletes a VM" do
    expect(client).to receive(:run_cli).with("vm delete 'foo'")

    client.delete_vm("foo").should be_true
  end

  it "finds VM by id" do
    vm = double(EsxCloud::Vm)
    expect(@api_client).to receive(:find_vm_by_id).with("foo").and_return(vm)
    client.find_vm_by_id("foo").should == vm
  end

  it "finds all VMs in a project" do
    vms = double(EsxCloud::VmList)
    expect(@api_client).to receive(:find_all_vms).with("foo").and_return(vms)
    client.find_all_vms("foo").should == vms
  end

  it "finds VMs in a project by name" do
    vms = double(EsxCloud::VmList)
    expect(@api_client).to receive(:find_vms_by_name).with("foo", "bar").and_return(vms)
    client.find_vms_by_name("foo", "bar").should == vms
  end

  it "gets VM network connections" do
    networks = double(EsxCloud::VmNetworks)
    expect(@api_client).to receive(:get_vm_networks).with("foo").and_return(networks)
    client.get_vm_networks("foo").should == networks
  end

  it "gets VM mks ticket" do
    mks_ticket = double(EsxCloud::MksTicket)
    expect(@api_client).to receive(:get_vm_mks_ticket).with("foo").and_return(mks_ticket)
    client.get_vm_mks_ticket("foo").should == mks_ticket
  end

  it "gets VM tasks" do
    tasks = double(EsxCloud::TaskList)
    expect(@api_client).to receive(:get_vm_tasks).with("foo", "bar").and_return(tasks)
    client.get_vm_tasks("foo", "bar").should == tasks
  end

  it "starts VM " do
    vm = double(EsxCloud::Vm, id: "vm1-id")

    expect(client).to receive(:run_cli).with("vm start 'vm1-id'")
    expect(client).to receive(:find_vm_by_id).once.with("vm1-id").and_return(vm)

    client.start_vm("vm1-id").should == vm
  end

  it "stops VM " do
    vm = double(EsxCloud::Vm, id: "vm1-id")

    expect(client).to receive(:run_cli).with("vm stop 'vm1-id'")
    expect(client).to receive(:find_vm_by_id).once.with("vm1-id").and_return(vm)

    client.stop_vm("vm1-id").should == vm
  end

  it "restarts VM " do
    vm = double(EsxCloud::Vm, id: "vm1-id")

    expect(client).to receive(:run_cli).with("vm restart 'vm1-id'")
    expect(client).to receive(:find_vm_by_id).once.with("vm1-id").and_return(vm)

    client.restart_vm("vm1-id").should == vm
  end

  it "resumes VM " do
    vm = double(EsxCloud::Vm, id: "vm1-id")

    expect(client).to receive(:run_cli).with("vm resume 'vm1-id'")
    expect(client).to receive(:find_vm_by_id).once.with("vm1-id").and_return(vm)

    client.resume_vm("vm1-id").should == vm
  end

  it "suspends VM " do
    vm = double(EsxCloud::Vm, id: "vm1-id")

    expect(client).to receive(:run_cli).with("vm suspend 'vm1-id'")
    expect(client).to receive(:find_vm_by_id).once.with("vm1-id").and_return(vm)

    client.suspend_vm("vm1-id").should == vm
  end

  it "performs VM disk attach" do
    vm = double(EsxCloud::Vm, :name => "vm_name")
    disk_id = "disk_id"

    expect(client).to receive(:run_cli).with("vm attach_disk 'vm_id' -d 'disk_id'")
    expect(client).to receive(:find_vm_by_id).once.with("vm_id").and_return(vm)

    client.perform_vm_disk_operation("vm_id", "ATTACH_DISK", disk_id).should == vm
  end

  it "performs VM disk detach" do
    vm = double(EsxCloud::Vm, :name => "vm_name")
    disk_id = "disk_id"

    expect(client).to receive(:run_cli).with("vm detach_disk 'vm_id' -d 'disk_id'")
    expect(client).to receive(:find_vm_by_id).once.with("vm_id").and_return(vm)

    client.perform_vm_disk_operation("vm_id", "DETACH_DISK", disk_id).should == vm
  end

  it "finds VMs by name" do
    vms = double(EsxCloud::VmList)
    expect(@api_client).to receive(:find_vms_by_name).with("p1", "vm1").and_return(vms)
    client.find_vms_by_name("p1", "vm1").should == vms
  end

  it "attaches an ISO" do
    vm = double(EsxCloud::Vm, id: "vm-id")
    expect(client).to receive(:run_cli)
                            .with("vm attach_iso 'vm-id' -p '/iso/path' -n 'iso-name'")
                            .and_return(task_created("task-id"))
    expect(client).to receive(:find_vm_by_id).once.with("vm-id").and_return(vm)

    client.perform_vm_iso_attach("vm-id", "/iso/path", "iso-name").should == vm
  end

  it "detaches an ISO" do
    vm = double(EsxCloud::Vm, id: "vm-id")
    expect(@api_client).to receive(:perform_vm_iso_detach).with("vm-id").and_return(vm)

    client.perform_vm_iso_detach("vm-id").should == vm
  end

  it "sets VM metadata" do
    vm = double(EsxCloud::Vm, id: "vm-id", metadata: {"key"=>"value"})
    expect(@api_client).to receive(:perform_vm_metadata_set).with("vm-id", {"key"=>"value"}).and_return(vm)

    client.perform_vm_metadata_set("vm-id", {"key"=>"value"}).should == vm
  end
end
