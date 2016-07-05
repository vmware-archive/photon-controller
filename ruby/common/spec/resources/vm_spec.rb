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

describe EsxCloud::Vm do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
    @vm = EsxCloud::Vm.new("foo", "vm-name", "core-100", "STARTED", "image-id", "1.1.1.1", "datastore",  [], [], [])
    @vm2 = EsxCloud::Vm.new("foo", "vm-name", "core-200", "N/A", "image-id", "1.1.1.1", "datastore",  [], [], [])
  end

  it "delegates create to API client" do
    spec = EsxCloud::VmCreateSpec.new(
        "name",
        "core-100",
        "image-id",
        [
            EsxCloud::VmDisk.new("disk-1", "ephemeral", "core-100", nil, true),
            EsxCloud::VmDisk.new("disk-2", "ephemeral", "core-100", 10, false)
        ]
    )
    spec_hash = {
        name: "name",
        flavor: "core-100",
        sourceImageId: "image-id",
        attachedDisks: [
            {name: "disk-1", kind: "ephemeral", flavor: "core-100", capacityGb: nil, bootDisk: true, id: nil},
            {name: "disk-2", kind: "ephemeral", flavor: "core-100", capacityGb: 10, bootDisk: false, id: nil},
        ],
        environment: {},
        affinities: [],
        subnets: []
    }

    expect(@client).to receive(:create_vm).with("foo", spec_hash)

    EsxCloud::Vm.create("foo", spec)
  end

  it "delegates delete to API client" do
    expect(@client).to receive(:delete_vm).with("foo")
    EsxCloud::Vm.delete("foo")
  end

  it "delegates start to API client" do
    expect(@client).to receive(:start_vm).with("foo")
    EsxCloud::Vm.start("foo")
  end

  it "delegates stop to API client" do
    expect(@client).to receive(:stop_vm).with("foo")
    EsxCloud::Vm.stop("foo")
  end

  it "delegates restart to API client" do
    expect(@client).to receive(:restart_vm).with("foo")
    EsxCloud::Vm.restart("foo")
  end

  it "delegates suspend to API client" do
    expect(@client).to receive(:suspend_vm).with("foo")
    EsxCloud::Vm.suspend("foo")
  end

  it "delegates resume to API client" do
    expect(@client).to receive(:resume_vm).with("foo")
    EsxCloud::Vm.resume("foo")
  end

  it "delegates disk attach to API client" do
    disk_id = "diskId1"
    expect(@client).to receive(:perform_vm_disk_operation).with("vmId", "ATTACH_DISK", disk_id)
    EsxCloud::Vm.attach_disk("vmId", disk_id)
  end

  it "delegates disk detach to API client" do
    disk_id = "diskId1"
    expect(@client).to receive(:perform_vm_disk_operation).with("vmId", "DETACH_DISK", disk_id)
    EsxCloud::Vm.detach_disk("vmId", disk_id)
  end

  it "delegates iso attach to API client" do
    expect(@client).to receive(:perform_vm_iso_attach).with("vmId", "iso-path", "iso-name")
    EsxCloud::Vm.attach_iso("vmId", "iso-path", "iso-name")
  end

  it "delegates iso detach to API client" do
    expect(@client).to receive(:perform_vm_iso_detach).with("vmId")
    EsxCloud::Vm.detach_iso("vmId")
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "vm_name",
        "state" => "STOPPED",
        "flavor" => "core-100",
        "sourceImageId" => "image-id",
        "host" => "1.1.1.1",
        "datastore" => "datastore",
        "attachedDisks" => [
            {
                "name" => "disk-1", "kind" => "ephemeral",
                "flavor" => "core-100", "capacityGb" => nil, "bootDisk" => true
            },
            {
                "name" => "disk-2", "kind" => "ephemeral",
                "flavor" => "core-100", "capacityGb" => 10, "bootDisk" => false
            },
        ],
        "attachedIsos" => [
            {
                "id" => "foo",
                "name" => "iso_name",
                "size" => 1234
            }
        ],
        "tags" => [
            "tag1",
            "tag2"
        ]
    }
    from_hash = EsxCloud::Vm.create_from_hash(hash)
    from_json = EsxCloud::Vm.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |vm|
      vm.id.should == "foo"
      vm.name.should == "vm_name"
      vm.state.should == "STOPPED"
      vm.flavor.should == "core-100"
      vm.source_image_id.should == "image-id"
      vm.host.should == "1.1.1.1"
      vm.datastore.should == "datastore"
      vm.disks.should == [
          EsxCloud::VmDisk.new("disk-1", "ephemeral", "core-100", nil, true),
          EsxCloud::VmDisk.new("disk-2", "ephemeral", "core-100", 10, false)
      ]
      vm.isos.should == [
          EsxCloud::Iso.new("foo", "iso_name", 1234)
      ]
      vm.tags.should == [
          "tag1",
          "tag2"
      ]
    end
  end

  it "doesn't complain if attachedDisks is missing" do
    hash = {
        "id" => "foo",
        "name" => "vm_name",
        "state" => "STOPPED",
        "flavor" => "core-100"
    }
    from_hash = EsxCloud::Vm.create_from_hash(hash)
    from_json = EsxCloud::Vm.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |vm|
      vm.id.should == "foo"
      vm.name.should == "vm_name"
      vm.state.should == "STOPPED"
      vm.flavor.should == "core-100"
      vm.disks.should == []
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::Vm.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "can be deleted" do
    expect(@client).to receive(:delete_vm).with("foo")
    @vm.delete
  end

  it "can be started" do
    expect(@client).to receive(:start_vm).with("foo").and_return(@vm2)

    @vm.start!
    @vm.should == @vm2
  end

  it "can be stopped" do
    expect(@client).to receive(:stop_vm).with("foo").and_return(@vm2)

    @vm.stop!
    @vm.should == @vm2
  end

  it "can be restarted" do
    expect(@client).to receive(:restart_vm).with("foo").and_return(@vm2)

    @vm.restart!
    @vm.should == @vm2
  end

  it "can be suspended" do
    expect(@client).to receive(:suspend_vm).with("foo").and_return(@vm2)

    @vm.suspend!
    @vm.should == @vm2
  end

  it "can be resumed" do
    expect(@client).to receive(:resume_vm).with("foo").and_return(@vm2)

    @vm.resume!
    @vm.should == @vm2
  end

end
