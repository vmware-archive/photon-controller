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

describe "vm disk ops", management: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new([create_limit("vm.memory", 15000.0, "GB")], [50])
    @cleaner = EsxCloud::SystemCleaner.new(client)
    @vm = @seeder.vm!
    @persistent_disk = @seeder.persistent_disk!
  end

  after(:each) do
    @vm.resume! if @vm.state == "SUSPENDED"
    @vm.stop! if @vm.state == "STARTED"
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
  end

  it "should attach and detach two valid disks" do
    persistent_disk2 = @seeder.project!.create_disk(
      name: random_name("disk-"),
      kind: "persistent-disk",
      flavor: @seeder.persistent_disk_flavor!.name,
      capacity_gb: 2,
      boot_disk: false,
      affinities: [{id: @vm.id, kind: "vm"}])

    @vm.attach_disk(@persistent_disk.id)
    @vm.attach_disk(persistent_disk2.id)
    attached_disk_names = @vm.get_attached_disk_names("persistent-disk")
    attached_disk_names.sort.should == [@persistent_disk.name, persistent_disk2.name].sort

    @vm.detach_disk(@persistent_disk.id)
    @vm.detach_disk(persistent_disk2.id)
    attached_disk_names = @vm.get_attached_disk_names("persistent-disk")
    attached_disk_names.should == []
  end

  it "should fail when a disk doesn't exist" do
    begin
      @vm.attach_disk("invalid-disk-id")
      fail "detach non-existing disks should have failed"
    rescue EsxCloud::ApiError => e
      e.response_code.should == 404
      e.errors.size.should == 1
      e.errors[0].code.should == "DiskNotFound"
    rescue EsxCloud::CliError => e
      e.message.should match("DiskNotFound")
    end
  end

  it "should fail to attach a disk to a vm not in the same project" do
    project2 = @seeder.tenant!.create_project(
        name: random_name("project-"),
        resource_ticket_name: @seeder.resource_ticket!.name,
        limits: [create_limit("vm.memory", 150.0, "GB")])

    persistent_disk3 = project2.create_disk(
        name: random_name("disk-"),
        kind: "persistent-disk",
        flavor: @seeder.persistent_disk_flavor!.name,
        capacity_gb: 2,
        boot_disk: false,
        affinities: [{id: @vm.id, kind: "vm"}])
    begin
      @vm.attach_disk(persistent_disk3.id)
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "InvalidEntity"
    rescue EsxCloud::CliError => e
      e.message.should match("InvalidEntity")
    end
  end

  it "should fail detach when disk is not attached" do
    begin
      @vm.detach_disk(@persistent_disk.id)
      fail "detach non-attached disks should have failed"
    rescue EsxCloud::ApiError => e
      e.response_code.should == 200
      e.errors.first.size.should == 1
      step_error = e.errors.first.first
      step_error.code.should == "InternalError"
    rescue EsxCloud::CliError => e
      if ENV["DRIVER"] == "gocli"
        e.message.should match("InternalError")
      else
        e.message.should include("API error: DETACH_DISK failed")
      end
    end
  end

  it "should fail attach when disk is already attached" do
    @vm.attach_disk(@persistent_disk.id)
    begin
      @vm.attach_disk(@persistent_disk.id)
      fail "attach already-attached disks should have failed"
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.first.code.should == "StateError"
      e.errors.first.message.should match(/Invalid operation ATTACH_DISK for persistent-disk.+in state ATTACHED/)
    rescue EsxCloud::CliError => e
      e.message.should match(/Invalid operation ATTACH_DISK for persistent-disk.+in state ATTACHED/)
    ensure
      @vm.detach_disk(@persistent_disk.id)
    end
  end

  it "should fail attach disk to two different vms" do
    begin
      @vm.attach_disk(@persistent_disk.id)
      attached_disk_names = @vm.get_attached_disk_names("persistent-disk")
      attached_disk_names.should == [@persistent_disk.name]

      vm2 = create_vm(@seeder.project!)
      vm2.attach_disk(@persistent_disk.id)
      fail "attach disk to two different vms should fail"
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.first.code.should == "StateError"
      e.errors.first.message.should match(/Invalid operation ATTACH_DISK for persistent-disk.+in state ATTACHED/)
    rescue EsxCloud::CliError => e
      e.message.should match(/Invalid operation ATTACH_DISK for persistent-disk.+in state ATTACHED/)
    ensure
      @vm.detach_disk(@persistent_disk.id)
      vm2.delete
    end
  end

  it "should fail when a VM doesn't exist" do
    begin
      EsxCloud::Vm.attach_disk("non-existing-vm", @persistent_disk.id)
      fail "attach disks to non-existent VM should have failed"
    rescue EsxCloud::ApiError => e
      e.response_code.should == 404
      e.errors.size.should == 1
      e.errors[0].code.should == "VmNotFound"
    rescue EsxCloud::CliError => e
      e.message.should match("VmNotFound")
    end
  end

  context "when VM is STOPPED" do
    it "should attach and detach disk" do
      @vm.attach_disk(@persistent_disk.id)
      attached_disk_names = @vm.get_attached_disk_names("persistent-disk")
      attached_disk_names.should == [@persistent_disk.name]

      @vm.detach_disk(@persistent_disk.id)
      attached_disk_names = @vm.get_attached_disk_names("persistent-disk")
      attached_disk_names.should == []
    end
  end

  context "when VM is STARTED" do
    before do
      @vm.start!
      @vm.state.should == "STARTED"
    end

    it "should attach and detach disk" do
      @vm.attach_disk(@persistent_disk.id)
      attached_disk_names = @vm.get_attached_disk_names("persistent-disk")
      attached_disk_names.should == [@persistent_disk.name]

      @vm.detach_disk(@persistent_disk.id)
      attached_disk_names = @vm.get_attached_disk_names("persistent-disk")
      attached_disk_names.should == []
    end
  end

  context "when VM is SUSPENDED" do
    before do
      @vm.start!
      @vm.suspend!
      @vm.state.should == "SUSPENDED"
    end

    it "should fail to attach disk" do
      begin
        @vm.attach_disk(@persistent_disk.id)
        fail "attach disk when vm is in SUSPENDED state should have failed"
      rescue EsxCloud::ApiError => e
        e.response_code.should == 200
        e.errors.size.should == 1
        step_error = e.errors.first.first
        step_error.code.should == "InvalidVmState"
        step_error.message.should include("Vm in suspended state")
      rescue EsxCloud::CliError => e
        e.message.should include("Vm in suspended state")
      end
    end
  end

  context "when VM is ERROR" do
    let(:persistent_disk) do
      @seeder.project!.create_disk(
        name: random_name("disk-"),
        kind: "persistent-disk",
        flavor: @seeder.persistent_disk_flavor!.name,
        capacity_gb: 2,
        boot_disk: false)
    end

    let(:vm) do
      vm_name = "error-vm"
      begin
        create_vm(@seeder.project!,
          name: vm_name, flavor: @seeder.huge_vm_flavor!.name,
          affinities: [{id: persistent_disk.id, kind: "disk"}])
        fail "create huge vm should have failed"
      rescue Exception => e
      end

      vms = client.find_all_vms(@seeder.project!.id).items
      vms.size.should == 2
      vm = vms.find { |v| v.name == vm_name }
      vm.flavor.should == @seeder.huge_vm_flavor!.name
      vm.state.should == "ERROR"
      vm
    end

    after(:each) do
      vm.delete
    end

    it "should fail to attach disk" do
      begin
        vm.attach_disk(persistent_disk.id)
        fail "attach disk when vm is in ERROR state should have failed"
      rescue EsxCloud::ApiError => e
        e.response_code.should == 200
        e.errors.size.should == 1
        step_error = e.errors.first.first
        step_error.code.should == "VmNotFound"
      rescue EsxCloud::CliError => e
        if ENV["DRIVER"] == "gocli"
          e.message.should match("VmNotFound")
        else
          e.message.should include("API error: ATTACH_DISK failed")
        end
      end
    end
  end
end
