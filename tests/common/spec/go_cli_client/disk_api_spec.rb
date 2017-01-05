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
  it "creates a DISK" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    disk_id = double("bar")
    disk = double(EsxCloud::Disk, :id => disk_id)

    spec = {
        name: "disk1",
        flavor: "core-100",
        capacityGb: 2,
        affinities: [
        {
            id: "vm-id1",
        kind: "vm",
    },
        {
            id: "vm-id2",
        kind: "vm",
    }],
        tags: ["tag1", "tag2"]
    }

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
                      .with("disk create -t 't1' -p 'p1' -n 'disk1' -f 'core-100' -c '2' " +
                            "-a 'vm:vm-id1, vm:vm-id2' -s 'tag1, tag2'")
                      .and_return(disk_id)
    expect(client).to receive(:find_disk_by_id).with(disk_id).and_return(disk)
    expect(client.create_disk("foo", spec)).to eq disk
    client.disk_to_project[disk_id].should == project
  end

  it "finds DISK by id" do
    disk_id = double("bar")
    disk_hash = { "id" => disk_id,
                  "name" => "disk1",
                  "state" => "DETACHED",
                  "kind" => "persisitent-disk",
                  "flavor" => "core-100",
                  "capacityGb" => 2,
                  "datastore" => "abcd",
                  "tags" => ["tag1", "tag2"],
                  "vms" => [] }
    disk = EsxCloud::Disk.create_from_hash(disk_hash)
    result = "bar  disk1  DETACHED  persistent-disk  core-100  2 abcd  tag1,tag2  "
    expect(client).to receive(:run_cli).with("disk show #{disk_id}").and_return(result)
    expect(client).to receive(:get_disk_from_response).with(result).and_return(disk)
    expect(client.find_disk_by_id(disk_id)).to eq disk
  end

  it "finds all disks" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    disks = double(EsxCloud::DiskList)
    result ="d1 disk1  READY
             d2 disk2  READY"

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli).with("disk list -t 't1' -p 'p1'").and_return(result)
    expect(client).to receive(:get_disk_list_from_response).with(result).and_return(disks)

    client.find_all_disks("foo").should == disks
  end

  it "finds disks by name" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "id", :name => "p1")
    client.project_to_tenant[project.id] = tenant

    result ="diskId1 disk1 READY"
    disks = double(EsxCloud::DiskList)

    expect(client).to receive(:find_project_by_id).with(project.id).and_return(project)
    expect(client).to receive(:run_cli).with("disk list -t 't1' -p 'p1' -n 'disk1'").and_return(result)
    expect(client).to receive(:get_disk_list_from_response).with(result).and_return(disks)

    expect(client.find_disks_by_name(project.id, "disk1")).to eq disks
  end

  context "when deleting a disk" do
    it "deletes a disk" do
      disk_id = double("bar")
      expect(client).to receive(:run_cli).with("disk delete '#{disk_id}'")

      client.delete_disk(disk_id).should be_true
    end
  end

  it "gets disk tasks" do
    disk_id = double("bar")
    result = "task1 COMPLETED CREATE_DISK  1458853080000  1000
              task2 COMPLETED DELETE_DISK  1458853089000  1000"
    tasks = double(EsxCloud::TaskList)
    expect(client).to receive(:run_cli).with("disk tasks '#{disk_id}' -s 'COMPLETED'").and_return(result)
    expect(client).to receive(:get_task_list_from_response).with(result).and_return(tasks)
    client.get_disk_tasks(disk_id, "COMPLETED").should == tasks
  end
end