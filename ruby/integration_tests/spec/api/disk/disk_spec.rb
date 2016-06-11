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

describe "disk", management: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new(create_small_limits, [5.0])
    @cleaner = EsxCloud::SystemCleaner.new(client)
    @project = @seeder.project!
    @vm = @seeder.vm!
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
  end

  let(:disk_name) { random_name("disk-") }
  let(:disk_flavor) { EsxCloud::SystemSeeder.instance.pending_delete_disk_flavor! }

  [
      "persistent-disk",
      "persistent"
  ].each do |kind|
    it "should create one disk with kind '#{kind}'" do
      disk = @project.create_disk(name: disk_name, kind: kind,
                                  flavor: @seeder.persistent_disk_flavor!.name,
                                  capacity_gb: 2, boot_disk: false, tags: ["disk_tag"])
      expect(disk.name).to eq disk_name
      expect(disk.flavor).to eq @seeder.persistent_disk_flavor!.name
      expect(disk.capacity_gb).to eq 2
      expect(disk.datastore).to_not be_empty
      expect(disk.tags).to eq ["disk_tag"]

      validate_disk_tasks(client.get_disk_tasks(disk.id))
      validate_disk_tasks(client.get_disk_tasks(disk.id, "COMPLETED"))

      disk.delete
    end
  end

  it "should not delete a project with a disk" do
    disk = @project.create_disk(name: disk_name, kind: "persistent-disk",
                                flavor: @seeder.persistent_disk_flavor!.name,
                                capacity_gb: 2, boot_disk: false)

    begin
      @project.delete
      fail("Project should not be deleted")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "ContainerNotEmpty"
    rescue EsxCloud::CliError => e
      e.output.should match("ContainerNotEmpty")
    end

    disk.delete
  end

  it "should complain on invalid name" do
    begin
      @project.create_disk(name: "1foo", kind: "persistent-disk",
                           flavor: @seeder.persistent_disk_flavor!.name,
                           capacity_gb: 1, boot_disk: true)
      fail("Creating a Disk with an invalid name should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "InvalidEntity"
    rescue EsxCloud::CliError => e
      e.output.should match("InvalidEntity")
    end
  end

  it "should allow duplicate disk names" do
    @project.create_disk(name: disk_name, kind: "persistent-disk",
                         flavor: @seeder.persistent_disk_flavor!.name,
                         capacity_gb: 2, boot_disk: true)
    @project.create_disk(name: disk_name, kind: "persistent-disk",
                         flavor: @seeder.persistent_disk_flavor!.name,
                         capacity_gb: 2, boot_disk: true)

    disks = client.find_all_disks(@project.id).items
    disks.size.should == 2

    if ENV["DRIVER"] != "gocli"
      disks = client.find_all_disks_pagination(@project.id).items
      disks.size.should == 2
    end

    disks.each do |disk|
      disk.name.should == disk_name
    end

    disks = client.find_disks_by_name(@project.id, disk_name).items
    disks.size.should == 2

    disks.each do |disk|
      disk.delete
    end
  end

  it "should return error with invalid flavor name" do
    begin
      @project.create_disk(name: disk_name, kind: "persistent-disk",
                           flavor: "core-not-exist",
                           capacity_gb: 2, boot_disk: true)
      fail("Creating a Disk with invalid flavor name should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "InvalidFlavor"
      e.errors[0].message.should include("not found");
    rescue EsxCloud::CliError => e
      e.output.should match("InvalidFlavor")
    end

    disks = client.find_disks_by_name(@project.id, disk_name).items
    disks.size.should == 0
  end

  it "should fail to create disk using PENDING_DELETE flavor" do
    begin
      expect(disk_flavor.state).to eq "PENDING_DELETE"

      @project.create_disk(name: random_name("disk-"), kind: "persistent-disk",
                           flavor: disk_flavor.name,
                           capacity_gb: 2, boot_disk: false)
      fail "create disk with flavor in PENDING_DELETE state should fail"
    rescue EsxCloud::ApiError => e
      expect(e.response_code).to eq 400
      expect(e.errors.size).to eq 1
      expect(e.errors[0].code).to eq "InvalidFlavorState"
    rescue EsxCloud::CliError => e
      expect(e.message).to match("InvalidFlavorState")
    end
  end

  it "should delete PENDING_DELETE flavor when delete disk" do
    flavor_name = random_name("flavor-")
    flavor_kind = "persistent-disk"
    flavor_cost = [create_limit("persistent-disk.cost", 1.0, "COUNT")]
    test_flavor = create_flavor(EsxCloud::FlavorCreateSpec.new(flavor_name, flavor_kind, flavor_cost))

    disk = @project.create_disk(name: random_name("disk-"), kind: "persistent-disk",
                         flavor: flavor_name,
                         capacity_gb: 2, boot_disk: false)
    test_flavor.delete
    flavor_list = client.find_flavors_by_name_kind(flavor_name, flavor_kind)
    expect(flavor_list.items.size).to eq 1
    expect(flavor_list.items[0].state).to eq "PENDING_DELETE"

    disk.delete
    flavor_list = client.find_flavors_by_name_kind(flavor_name, flavor_name)
    expect(flavor_list.items).to be_empty
  end

  context "failure at agent call" do
    it "should fail requesting a disk too big" do
      disk_name = random_name("disk-")

      begin
        @project.create_disk(name: disk_name, kind: "persistent-disk",
                             flavor: @seeder.persistent_disk_flavor!.name,
                             capacity_gb: 999999, boot_disk: false)
      rescue EsxCloud::ApiError => e
        e.response_code.should == 200
        e.errors.size.should == 1
        e.errors.first.size.should == 1
        step_error = e.errors.first.first
        step_error.code.should == "NotEnoughDatastoreCapacity"
        step_error.step["operation"].should == "RESERVE_RESOURCE"
      rescue EsxCloud::CliError => e
        e.output.should match("NotEnoughDatastoreCapacity")
      end

      disks = client.find_disks_by_name(@project.id, disk_name).items
      disks.size.should == 1
      disk = disks.first
      disk.flavor.should == @seeder.persistent_disk_flavor!.name
      disk.name.should == disk_name
      disk.state.should == "ERROR"

      disk.delete
    end

  end

  context "with datastore_tag resource constraint", devbox: true do
    context "with local_vmfs disk flavor" do
      it "fails on shared datastore" do
        begin
          project = EsxCloud::SystemSeeder.instance.project!
          disk_name = random_name("disk-")
          disk1 = project.create_disk(name: disk_name, kind: "persistent-disk",
              flavor: EsxCloud::SystemSeeder.instance.persistent_disk_flavor_with_local_tag!.name,
              capacity_gb: 2, boot_disk: false)

          fail("should fail when a disk with local_vmfs flavor is placed on shared datastore")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq(200)
          expect(e.errors.size).to eq(1)
          expect(e.errors.first.size).to eq(1)
          step_error = e.errors.first.first
          expect(step_error.code).to eq("UnfullfillableDiskAffinities")
          expect(step_error.step["operation"]).to eq("RESERVE_RESOURCE")
        rescue EsxCloud::CliError => e
          expect(e.output).to eq("UnfullfillableDiskAffinities")
        end
      end
    end

    context "with shared_vmfs disk flavor" do
      it "succeeds on shared datastore" do
        project = EsxCloud::SystemSeeder.instance.project!
        disk_name = random_name("disk-")
        disk1 = project.create_disk(name: disk_name, kind: "persistent-disk",
            flavor: EsxCloud::SystemSeeder.instance.persistent_disk_flavor_with_shared_tag!.name,
            capacity_gb: 2, boot_disk: false)
        expect(disk1.name).to eq(disk_name)
      end
    end
  end

  context "with VM affinity" do
    it "should create one persistent disk with one affinity setting" do
      disk = @project.create_disk(name: disk_name, kind: "persistent-disk",
          flavor: @seeder.persistent_disk_flavor!.name,
          capacity_gb: 2, boot_disk: false,
          affinities: [{id: @vm.id, kind: "vm"}])
      disk.name.should == disk_name

      validate_disk_tasks(client.get_disk_tasks(disk.id))
      validate_disk_tasks(client.get_disk_tasks(disk.id, "COMPLETED"))

      disk.delete
    end

    it "should create two persistent disk with same affinity setting" do
      disk1 = @project.create_disk(name: disk_name, kind: "persistent-disk",
          flavor: @seeder.persistent_disk_flavor!.name,
          capacity_gb: 2, boot_disk: false,
          affinities: [{id: @vm.id, kind: "vm"}])
      disk1.name.should == disk_name

      disk_name = random_name("disk-")
      disk2 = @project.create_disk(name: disk_name, kind: "persistent-disk",
          flavor: @seeder.persistent_disk_flavor!.name,
          capacity_gb: 2, boot_disk: false,
          affinities: [{id: @vm.id, kind: "vm"}])
      disk2.name.should == disk_name

      disk1.delete
      disk2.delete
    end
  end

  private

  def validate_disk_tasks(task_list)
    tasks = task_list.items
    tasks.size.should == 1
    task = tasks.first
    [task.entity_kind, task.operation].should == ["persistent-disk", "CREATE_DISK"]
  end
end
