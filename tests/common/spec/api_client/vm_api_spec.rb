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

describe EsxCloud::ApiClient do

  before(:each) do
    @http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(@http_client)
  end

  let(:client) {
    EsxCloud::ApiClient.new("localhost:9000")
  }

  it "creates a VM" do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
                            .with("/projects/foo/vms", "payload")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.create_vm("foo", "payload").should == vm
  end

  it "deletes a VM" do
    expect(@http_client).to receive(:delete).with("/vms/foo").and_return(task_created("aaa"))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_vm("foo").should be_true
  end

  it "finds all VMs" do
    vms = double(EsxCloud::Vm)

    expect(@http_client).to receive(:get).with("/projects/foo/vms").and_return(ok_response("vms"))
    expect(EsxCloud::VmList).to receive(:create_from_json).with("vms").and_return(vms)

    client.find_all_vms("foo").should == vms
  end

  it "finds VM by id" do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:get).with("/vms/foo")
                            .and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.find_vm_by_id("foo").should == vm
  end

  it "finds VMs by name" do
    vms = double(EsxCloud::VmList)

    expect(@http_client).to receive(:get).with("/projects/foo/vms?name=bar")
                            .and_return(ok_response("vms"))
    expect(EsxCloud::VmList).to receive(:create_from_json).with("vms").and_return(vms)

    client.find_vms_by_name("foo", "bar").should == vms
  end

  it "gets VM tasks" do
    tasks = double(EsxCloud::TaskList)

    expect(@http_client).to receive(:get).with("/vms/foo/tasks")
                            .and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

    client.get_vm_tasks("foo").should == tasks
  end

  it "gets VM network connections" do
    network_connections = double(EsxCloud::VmNetworks)

    expect(@http_client).to receive(:get).with("/vms/foo/networks")
                            .and_return(task_created("bar"))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/bar")
                            .and_return(task_done("bar", "id", "vm", "networks"))
    expect(EsxCloud::VmNetworks).to receive(:create_from_task)
                                    .and_return(network_connections)

    client.get_vm_networks("foo").should == network_connections
  end

  it "gets VM mks ticket" do
    mks_ticket = double(EsxCloud::MksTicket)
    expect(@http_client).to receive(:get).with("/vms/foo/mks_ticket")
                            .and_return(task_created("bar"))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/bar")
                            .and_return(task_done("bar", "id", "vm", "ticket"))
    expect(EsxCloud::MksTicket).to receive(:create_from_hash).with("ticket").and_return(mks_ticket)

    client.get_vm_mks_ticket("foo").should == mks_ticket
  end

  it "starts VM " do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
    .with("/vms/foo/start", {})
    .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.start_vm("foo").should == vm
  end

  it "stops VM " do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
    .with("/vms/foo/stop", {})
    .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.stop_vm("foo").should == vm
  end

  it "restarts VM " do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
    .with("/vms/foo/restart", {})
    .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.restart_vm("foo").should == vm
  end

  it "resumes VM " do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
    .with("/vms/foo/resume", {})
    .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.resume_vm("foo").should == vm
  end

  it "suspends VM " do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
    .with("/vms/foo/suspend", {})
    .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.suspend_vm("foo").should == vm
  end

  describe "#perform_vm_disk_operation" do
    context "when a disk is attached to a VM" do
      it "the disk is properly attached" do
        vm = double(EsxCloud::Vm)
        disk_id = "disk-id-1"

        expect(@http_client).to receive(:post_json)
                                .with("/vms/vm-id/attach_disk", {:diskId => disk_id, :arguments => {}})
                                .and_return(task_created("task-id"))

        expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/task-id").and_return(task_done("task-id", "vm-id"))
        expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
        expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

        client.perform_vm_disk_operation("vm-id", "attach_disk", disk_id, {}).should == vm
      end
    end

    context "when a disk is detached from a VM" do
      it "the disk is properly detached" do
        vm = double(EsxCloud::Vm)
        disk_id = "disk-id-1"

        expect(@http_client).to receive(:post_json)
                                .with("/vms/vm-id/detach_disk", {:diskId => disk_id, :arguments => {}})
                                .and_return(task_created("task-id"))

        expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/task-id").and_return(task_done("task-id", "vm-id"))
        expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
        expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

        client.perform_vm_disk_operation("vm-id", "detach_disk", disk_id, {}).should == vm
      end
    end
  end

  it "attaches an ISO" do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:upload)
                            .with("/vms/vm-id/attach_iso", "/iso/path", nil)
                            .and_return(task_created("task-id"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/task-id").and_return(task_done("task-id", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.perform_vm_iso_attach("vm-id", '/iso/path').should == vm
  end

  it "detaches an ISO" do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
                            .with("/vms/vm-id/detach_iso", nil)
                            .and_return(task_created("task-id"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/task-id").and_return(task_done("task-id", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.perform_vm_iso_detach("vm-id").should == vm
  end

  it "sets VM metadata" do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
                            .with("/vms/vm-id/set_metadata", {"kye"=>"value"})
                            .and_return(task_created("task-id"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/task-id").and_return(task_done("task-id", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.perform_vm_metadata_set("vm-id", {"kye"=>"value"}).should == vm
  end

  it "acquires floating IP for VM" do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:post_json)
                            .with("/vms/vm-id/acquire_floating_ip", {"networkId"=>"networkId"})
                            .and_return(task_created("task-id"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/task-id").and_return(task_done("task-id", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.acquire_floating_ip("vm-id", {"networkId"=>"networkId"}).should == vm
  end

  it "releases floating IP from VM" do
    vm = double(EsxCloud::Vm)

    expect(@http_client).to receive(:delete)
                            .with("/vms/vm-id/release_floating_ip")
                            .and_return(task_created("task-id"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/task-id").and_return(task_done("task-id", "vm-id"))
    expect(@http_client).to receive(:get).with("/vms/vm-id").and_return(ok_response("vm"))
    expect(EsxCloud::Vm).to receive(:create_from_json).with("vm").and_return(vm)

    client.release_floating_ip("vm-id").should == vm
  end
end
