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

describe EsxCloud::Disk do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
    @disk = EsxCloud::Disk.new("foo", "disk-name", "ephemeral", "core-100", 2, "datastore1", ["vm-id"], ["tag1","tag2"])
  end

  describe "#create" do
    it "delegates create to API client" do
      spec = EsxCloud::DiskCreateSpec.new(
            "disk_name",
            "ephemeral",
            "core-100",
            2,
            [{:id => "vm1", :kind => "vm"}],
            ["tag1","tag2"]
      )
      spec_hash = {
          :name => "disk_name",
          :kind => "ephemeral",
          :flavor => "core-100",
          :capacityGb => 2,
          :affinities=>[{:id => "vm1", :kind => "vm"}],
          :tags=>["tag1","tag2"]
      }

      expect(@client).to receive(:create_disk).with("foo", spec_hash)

      EsxCloud::Disk.create("foo", spec)
    end
  end

  describe "#delete" do
    it "delegates delete to API client" do
      expect(@client).to receive(:delete_disk).with("foo")
      EsxCloud::Disk.delete("foo")
    end
  end

  describe "#create_from_hash, #create_from_json" do

    context "when both jason and hash contain all required keys" do
      it "can be created from hash or from JSON" do
        hash = {
            "id" => "foo",
            "name" => "disk_name",
            "kind" => "ephemeral",
            "flavor" => "core-100",
            "capacityGb" => 2,
            "datastore" => "datastore1",
            "tags" => ["tag1", "tag2"],
            "state" => "DETACHED"
        }
        from_hash = EsxCloud::Disk.create_from_hash(hash)
        from_json = EsxCloud::Disk.create_from_json(JSON.generate(hash))

        [from_hash, from_json].each do |disk|
          disk.id.should == "foo"
          disk.name.should == "disk_name"
          disk.kind.should == "ephemeral"
          disk.flavor.should == "core-100"
          disk.capacity_gb.should == 2
          disk.datastore == "datastore1"
          disk.tags == ["tag1", "tag2"]
          disk.state.should == "DETACHED"
        end
      end
    end

    context "when hash contains no key" do
      it "expects hash to have all required keys" do
        expect { EsxCloud::Disk.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
      end
    end
  end

  describe "#delete" do
    it "can be deleted" do
      expect(@client).to receive(:delete_disk).with("foo")
      @disk.delete
    end
  end

  describe "#operator==" do
    context "when two Disks are equal" do
      it "returns true" do
        disk1 = EsxCloud::Disk.new(@disk.id, @disk.name, @disk.kind, @disk.flavor, @disk.capacity_gb, @disk.datastore, @disk.vms, @disk.tags)
        expect(@disk).to eq disk1
      end
    end
    context "when two Disks are different (id)" do
      it "returns false" do
        disk1 = EsxCloud::Disk.new(@disk.id + "not", @disk.name, @disk.kind, @disk.flavor, @disk.capacity_gb , @disk.datastore, @disk.vms, @disk.tags)
        expect(@disk).to_not eq disk1
      end
    end
    context "when two Disks are different (everything but id)" do
      it "returns false" do
        disk_capacity_gb = @disk.capacity_gb + 1
        disk1 = EsxCloud::Disk.new(@disk.id, @disk.name + "not", @disk.kind + "not", @disk.flavor + "not", disk_capacity_gb, @disk.datastore, @disk.vms, @disk.tags)
        expect(@disk).to_not eq disk1
      end
    end
    context "when two Disks are different (null kind)" do
      it "returns false" do
        disk1 = EsxCloud::Disk.new(@disk.id, @disk.name, nil, @disk.flavor, @disk.capacity_gb, @disk.datastore, @disk.vms, @disk.tags)
        expect(@disk).to_not eq disk1
      end
    end
  end

end
