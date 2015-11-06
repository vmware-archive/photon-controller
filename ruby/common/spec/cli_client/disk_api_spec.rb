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

  it "creates a DISK" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    disk = double(EsxCloud::Disk, :id => "bar")
    disks = double(EsxCloud::DiskList, :items => [disk])

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
                      .with("disk create -t 't1' -p 'p1' -n 'disk1' -f 'core-100' -g '2' " +
                         "-a 'vm vm-id1, vm vm-id2' -s 'tag1, tag2'")

    expect(client).to receive(:find_disks_by_name).with("foo", "disk1").and_return(disks)

    client.create_disk("foo", spec).should == disk
    client.disk_to_project["bar"].should == project
  end

  context "when deleting a disk" do
    it "deletes a disk" do
      expect(client).to receive(:run_cli).with("disk delete 'foo'")

      client.delete_disk("foo").should be_true
    end
  end

  it "finds DISK by id" do
    disk = double(EsxCloud::Disk)
    expect(@api_client).to receive(:find_disk_by_id).with("foo").and_return(disk)
    client.find_disk_by_id("foo").should == disk
  end

  it "finds all DISKs in a project" do
    disks = double(EsxCloud::DiskList)
    expect(@api_client).to receive(:find_all_disks).with("foo").and_return(disks)
    client.find_all_disks("foo").should == disks
  end

  it "finds DISKs in a project by name" do
    disks = double(EsxCloud::DiskList)
    expect(@api_client).to receive(:find_disks_by_name).with("foo", "bar").and_return(disks)
    client.find_disks_by_name("foo", "bar").should == disks
  end

  it "gets DISK tasks" do
    tasks = double(EsxCloud::TaskList)
    expect(@api_client).to receive(:get_disk_tasks).with("foo", "bar").and_return(tasks)
    client.get_disk_tasks("foo", "bar").should == tasks
  end

  it "finds DISKs by name" do
    disks = double(EsxCloud::DiskList)
    expect(@api_client).to receive(:find_disks_by_name).with("p1", "disk1").and_return(disks)
    client.find_disks_by_name("p1", "disk1").should == disks
  end
end
