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

require 'resolv'
require "spec_helper"

describe "vm", management: true, image: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new(create_small_limits, [5.0])
    @cleaner = EsxCloud::SystemCleaner.new(client)
    @project = @seeder.project!
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
  end

  it "should create one vm with two ephemeral disks successfully" do
    vm_name = random_name("vm-")
    disks = create_ephemeral_disks(["#{vm_name}-disk1", "#{vm_name}-disk2"])
    vm = create_vm(@project, name: vm_name, disks: disks)
    vm.name.should == vm_name

    validate_vm_tasks(client.get_vm_tasks(vm.id))
    validate_vm_tasks(client.get_vm_tasks(vm.id, "COMPLETED"))

    vm.delete
  end

  it "should fail to create one vm with two ephemeral disks on two datastores" do
    vm_name = random_name("vm-")
    edisk_flavor_local = @seeder.ephemeral_disk_flavor_with_local_tag!
    edisk_flavor_shared = @seeder.ephemeral_disk_flavor_with_shared_tag!
    disks = [EsxCloud::VmDisk.new("#{vm_name}-disk1", "ephemeral-disk", edisk_flavor_local.name, nil, true),
             EsxCloud::VmDisk.new("#{vm_name}-disk2", "ephemeral-disk", edisk_flavor_shared.name, 1, false)]

    err_msg = "Check disk(s) affinity. In case of more than one disk affinities, disk flavors may be incompatible."
    begin
      create_vm(@project, name: vm_name, disks: disks)
      fail("Create VM with two ephemeral disks on two datastores should fail")
    rescue EsxCloud::ApiError => e
      expect(e.response_code).to eq(200)
      expect(e.errors.size).to eq(1)
      expect(e.errors.first.size).to eq(1)
      step_error = e.errors.first.first
      expect(step_error.code).to eq("UnfullfillableDiskAffinities")
      expect(step_error.step["operation"]).to eq("RESERVE_RESOURCE")
      expect(step_error.message).to eq(err_msg)
    rescue EsxCloud::CliError => e
      expect(e.output).to include("UnfullfillableDiskAffinities")
      expect(e.output).to include(err_msg)
    ensure
      vms = client.find_all_vms(@project.id).items
      vms.each do |vm|
        vm.delete
      end
    end
  end

# Test disabled for cli as cli always creates the disk of kind "ephemeral-disk" while creating VM
  it "should fail to create one vm with one ephemeral disk and one persistent disk", disable_for_cli_test: true do
    vm_name = random_name("vm-")
    edisk_flavor = @seeder.ephemeral_disk_flavor!
    pdisk_flavor = @seeder.persistent_disk_flavor!
    disks = [EsxCloud::VmDisk.new("#{vm_name}-disk1", "ephemeral-disk", edisk_flavor.name, nil, true),
             EsxCloud::VmDisk.new("#{vm_name}-disk2", "persistent-disk", pdisk_flavor.name, 1, false)]

    error_message = "attachedDisks[1].kind must match \"ephemeral-disk|ephemeral\" (was persistent-disk)"
    begin
      create_vm(@project, name: vm_name, disks: disks)
      fail("Persistent disk should fail to attach during VM creation")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "InvalidEntity"
      e.errors[0].message.should == error_message
    rescue EsxCloud::CliError => e
      e.output.should match(error_message)
    end
  end

  it "should fail to delete vm powered on" do
    vm_name = random_name("vm-")
    vm = create_vm(@project, name: vm_name)
    vm.name.should == vm_name
    vm.start!

    begin
      vm.delete
      fail("Fail to delete vm with power on")
    rescue EsxCloud::ApiError => e
      expect(e.response_code).to eq(200)
      expect(e.errors.size).to eq(1)
      expect(e.errors.first.size).to eq(1)
      step_error = e.errors.first.first
      expect(step_error.code).to eq("InvalidVmState")
      expect(step_error.message).to eq("VM #{vm.id} not powered off")
    rescue EsxCloud::CliError => e
      e.output.should match /VM [\/\w\-\#]+ not powered off/
    ensure
      vm.stop!
      vm.delete
    end
  end

  it "should not delete a project with a vm" do
    vm = create_vm(@project)

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

    vm.delete
  end

  it "complains about unknown flavor" do
    error_msg = "Flavor unknown-100 is not found for kind vm"

    begin
      create_vm(@project, flavor: "unknown-100")
      fail("Create VM with unknown flavor should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "InvalidFlavor"
      e.errors[0].message.should match(error_msg)
    rescue EsxCloud::CliError => e
      e.output.should match("InvalidFlavor")
      e.output.should match(error_msg)
    end
  end

  it "complains about image not found" do
    error_msg = "Image id 'image-not-exist' not found"

    begin
      create_vm(@project, image_id: "image-not-exist")
      fail("Create VM with unknown image should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 404
      e.errors.size.should == 1
      e.errors[0].code.should == "ImageNotFound"
      e.errors[0].message.should match(error_msg)
    rescue EsxCloud::CliError => e
      e.output.should match("ImageNotFound")
      e.output.should match(error_msg)
    end
  end

  it "should complain on invalid name" do
    begin
      disks = create_ephemeral_disks(["foo-disk1", "foo-disk2"])
      create_vm(@project, :name => "1foo", :disks => disks)
      fail("Creating a VM with an invalid name should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "InvalidEntity"
    rescue EsxCloud::CliError => e
      e.output.should match("InvalidEntity")
    end
  end

  it "should retrieves the host Ip" do
    vm_id = create_vm(@project).id
    vm = client.find_vm_by_id(vm_id)
    vm.host.should_not be_nil
    vm.host.should =~ Resolv::IPv4::Regex
    vm.delete
  end

  it "should retrieves the datastore" do
    vm_id = create_vm(@project).id
    vm = client.find_vm_by_id(vm_id)
    vm.datastore.should_not be_nil
    vm.delete
  end

  it "should allow duplicate VM names", disable_for_cli_test: true do
    vm_name = random_name("vm-")
    create_vm(@project, name: vm_name)
    create_vm(@project, name: vm_name)

    vms = client.find_all_vms(@project.id).items
    vms.size.should == 2

    vms.each do |vm|
      vm.name.should == vm_name
    end

    vms = client.find_vms_by_name(@project.id, vm_name).items
    vms.each do |vm|
      vm.delete
    end
  end

  context "when attributes are specified in VmCreateSpec" do
    context "when valid attributes are specified in VmCreateSpec" do
      let(:vm_name) { random_name("vm-") }

      after(:each) do
        vms = client.find_vms_by_name(@project.id, vm_name).items
        vms.each do |vm|
          vm.delete
        end
      end

      context "when environment are specified" do

        it "should be able to pass environment to VM" do
          create_vm(@project, name: vm_name,
                    environment: {"foo" => "bar", "bar" => "baz"})

          vms = client.find_vms_by_name(@project.id, vm_name).items
          expect(vms.size).to eq 1
          expect(vms[0].state).to eq "STOPPED"
        end
      end

      context "when affinities are specified" do
        before(:all) do
          wait_for_image_seeding_progress_is_done
        end

        it "should fail to delete vm with persistent disk attached" do
          persistent_disk = @project.create_disk(
              name: random_name("disk-"),
              kind: "persistent-disk",
              flavor: @seeder.persistent_disk_flavor!.name,
              capacity_gb: 2,
              boot_disk: false)

          vm_name = random_name("vm-")
          vm = create_vm(@project, name: vm_name,
              affinities: [{id: persistent_disk.id, kind: "disk"}])
          vm.name.should == vm_name

          vm.attach_disk(persistent_disk.id)

          begin
            vm.delete
            fail("Fail to delete vm with persistent disk attached")
          rescue EsxCloud::ApiError => e
            e.response_code.should == 400
            e.errors.size.should == 1
            e.errors[0].code.should == "PersistentDiskAttached"
            e.errors[0].message.should match /Disk [\w\-#\/]+ is attached to [\w\-#\/]+/
          rescue EsxCloud::CliError => e
            e.output.should match /Disk [\w\-#\/]+ is attached to [\w\-#\/]+/
          ensure
            vm.detach_disk(persistent_disk.id)
            vm.delete
          end
        end

        # it "should create a vm with multiple nics on different networks", disable_for_cli_test: true, single_vm_port_group: true  do
        #   begin
        #     network1 = EsxCloud::SystemSeeder.instance.network!
        #
        #     pg2 = EsxCloud::TestHelpers.get_vm_port_group2
        #     spec = EsxCloud::NetworkCreateSpec.new(random_name("network-"), "VM Network2", [pg2])
        #     network2 = EsxCloud::Config.client.create_network(spec.to_hash)
        #
        #     vm_name = random_name("vm-")
        #     create_vm(@project, name: vm_name, networks: [network1.id, network2.id])
        #
        #     vms = client.find_vms_by_name(@project.id, vm_name).items
        #     expect(vms.size).to eq 1
        #     vm = vms[0]
        #
        #     networks = client.get_vm_networks(vm.id).network_connections
        #     network_ids = networks.map { |n| n.network }
        #     network_ids.should =~ [network1.id, network2.id]
        #   ensure
        #     ignoring_all_errors { vm.delete if vm }
        #     ignoring_all_errors { network2.delete if network2 }
        #   end
        # end

        it "can pass one disk affinity" do
          persistent_disks1 = @seeder.persistent_disk!

          create_vm(@project, name: vm_name,
                    affinities: [{id: persistent_disks1.id, kind: "disk"}])

          vms = client.find_vms_by_name(@project.id, vm_name).items
          expect(vms.size).to eq 1
          expect(vms[0].datastore).to eq persistent_disks1.datastore
          expect(vms[0].state).to eq "STOPPED"
        end

        it "can pass host IP", real_agent: true do

          create_vm(@project, name: vm_name,
                    affinities: [{id: get_esx_ip, kind: "host"}])

          vms = client.find_vms_by_name(@project.id, vm_name).items
          expect(vms.size).to eq 1
          expect(vms[0].host).to eq get_esx_ip
          expect(vms[0].state).to eq "STOPPED"
        end

        it "can pass portGroup" do

          port_group_affinity = EsxCloud::TestHelpers.get_vm_port_group

          create_vm(@project, name: vm_name,
                    affinities: [{id: port_group_affinity, kind: "portGroup"}])

          vms = client.find_vms_by_name(@project.id, vm_name).items
          expect(vms.size).to eq 1
          expect(vms[0].state).to eq "STOPPED"

          # verify one and only one network contains the port group we specified, and that network contains no other
          # port group
          networks = client.find_all_networks.items.select {|network| network.portgroups.include?(port_group_affinity)}
          expect(networks.size).to be(1)
          expect(networks[0].portgroups.size).to be(1)

          # verify network connections
          vm_networks = client.get_vm_networks(vms[0].id).network_connections
          vm_network_ids = vm_networks.map { |n| n.network }
          expect(vm_network_ids).to match_array([networks[0].id])
        end

        it "can pass datastore id", datastore_id:true do

          create_vm(@project, name: vm_name,
                    affinities: [{id: get_datastore_id, kind: "datastore"}])

          vms = client.find_vms_by_name(@project.id, vm_name).items
          expect(vms.size).to eq 1
          expect(vms[0].datastore).to eq get_datastore_id
          expect(vms[0].state).to eq "STOPPED"
        end

        it "can pass both host IP and datastore ID", datastore_id:true do
          host_affinity = {id: get_esx_ip, kind: "host"}
          datastore_affinity = {id: get_datastore_id, kind: "datastore"}

          create_vm(@project, name: vm_name,
                    affinities: [host_affinity, datastore_affinity])

          vms = client.find_vms_by_name(@project.id, vm_name).items
          expect(vms.size).to eq 1
          expect(vms[0].datastore).to eq get_datastore_id
          expect(vms[0].host).to eq get_esx_ip
          expect(vms[0].state).to eq "STOPPED"
        end
      end
    end

    context "when invalid attributes are specified in VmCreateSpec" do
      it "should complain about invalid locality spec" do
        begin
          disk_affinity = {id: "fake-disk-id", kind: "disk"}
          host_affinity = {id: get_esx_ip, kind: "host"}

          create_vm(@project, affinities: [disk_affinity, host_affinity])
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "InvalidEntity"
        rescue EsxCloud::CliError => e
          e.message.should match("InvalidEntity")
        end
      end

      it "should complain about vm affinity" do
        begin
          vm_affinity = {id: "vm-id", kind: "vm"}

          create_vm(@project, affinities: [vm_affinity])
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "InvalidEntity"
        rescue EsxCloud::CliError => e
          e.message.should match("InvalidEntity")
        end
      end

      it "should complain about two host affinities" do
        begin
          host_affinity_1 = {id: get_esx_ip, kind: "host"}
          host_affinity_2 = {id: "1.1.1.1", kind: "host"}

          create_vm(@project, affinities: [host_affinity_1, host_affinity_2])
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "InvalidEntity"
          e.errors[0].message.should include("A VM can only be affixed on one host")
        rescue EsxCloud::CliError => e
          e.message.should include("A VM can only be affixed on one host")
        end
      end

      it "should complain about two datastore affinities" do
        begin
          datastore_affinity_1 = {id: get_datastore_id, kind: "datastore"}
          datastore_affinity_2 = {id: "datastore-id", kind: "datastore"}

          create_vm(@project, affinities: [datastore_affinity_1, datastore_affinity_2])
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "InvalidEntity"
          e.errors[0].message.should include("A VM can only be affixed on one datastore")
        rescue EsxCloud::CliError => e
          e.message.should include("A VM can only be affixed on one datastore")
        end
      end
    end

  end

  context "when image is provided" do
    describe "#create" do
      let(:vm) do
        create_vm(@project, image_id: image.id)
      end

      context "when vm's image is in READY state" do
        let(:image) { EsxCloud::SystemSeeder.instance.bootable_image! }
        let(:network_id) { EsxCloud::SystemSeeder.instance.network!.id }
        let(:vm) do
          create_vm(@project, image_id: image.id, networks: [network_id])
        end

        after(:each) do
          vm.stop! if vm.state == "STARTED"
          vm.delete
        end

        it "should create one from new image", disable_for_cli_test: true do
          expect(image.state).to eq "READY"
          validate_vm_tasks(client.get_vm_tasks(vm.id))
          validate_vm_tasks(client.get_vm_tasks(vm.id, "COMPLETED"))

          expect(client.find_vm_by_id(vm.id).source_image_id).to eq image.id

          next unless ENV["REAL_AGENT"]

          vm.start!

          mks_ticket = client.get_vm_mks_ticket(vm.id)
          expect(mks_ticket.port).to be > 0
          expect(mks_ticket.cfg_file).to_not be_nil
          expect(mks_ticket.ticket).to match(/^[\w-]{20,}$/)
          expect(mks_ticket.ssl_thumbprint).to_not be_nil

          next unless ENV["DHCP_ENABLED"]

          retries = ENV["VM_IP_READ_RETRIES"] || 20
          sleep_time = 5
          puts("Starting vm and try up to #{retries} times with #{sleep_time} sleep seconds to get its ip address...")

          ip_address, connection_network_id = nil, nil
          retries.times do
            network_connections = client.get_vm_networks(vm.id).network_connections
            network_connection = network_connections.first
            ip_address = network_connection.ip_address
            connection_network_id = network_connection.network

            break unless ip_address.nil? || ip_address.empty?

            puts network_connections.inspect
            sleep(sleep_time)
          end

          puts "Got ip address: #{ip_address}"
          expect(ip_address).not_to be_blank
          ip_address.should =~ Resolv::IPv4::Regex
          is_pingable?(ip_address).should be_true
          is_ssh_open?(ip_address).should be_true
          expect(connection_network_id).to eq network_id
        end
      end

      context "when vm's image is in PENDING_DELETE state" do
        let(:image) { EsxCloud::SystemSeeder.instance.pending_delete_image! }

        it "should fail" do
          expect(image.state).to eq "PENDING_DELETE"

          begin
            create_vm(@project, image_id: image.id)
            fail "create vm with image in PENDING_DELETE state should fail"
          rescue EsxCloud::ApiError => e
            e.response_code.should == 400
            e.errors.size.should == 1
            e.errors[0].code.should == "InvalidImageState"
          rescue EsxCloud::CliError => e
            e.message.should match("InvalidImageState")
          end
        end
      end

      context "when vm's image is in ERROR state" do
        let(:image) { EsxCloud::SystemSeeder.instance.error_image! }

        it "should fail" do
          image.state.should == "ERROR"

          begin
            create_vm(@project, image_id: image.id)

            fail "create vm with image in ERROR state should fail"
          rescue EsxCloud::ApiError => e
            e.response_code.should == 400
            e.errors.size.should == 1
            e.errors[0].code.should == "InvalidImageState"
          rescue EsxCloud::CliError => e
            e.message.should match("InvalidImageState")
          end
        end
      end
    end

    describe "#delete" do
      context "when vm's image is in READY state" do
        let(:image) { EsxCloud::SystemSeeder.instance.image! }

        it "should not delete its image" do
          image_id = image.id
          vm = create_vm(@project)

          image.state.should == "READY"
          vm.delete

          image = EsxCloud::Image.find_by_id(image_id)
          expect(image).to_not be_nil
          expect(image.state).to eq "READY"
        end
      end

      context "when vm's image is in PENDING_DELETE state" do
        let(:image) do
          image_file = ENV["ESXCLOUD_DISK_OVA_IMAGE"] || fail("ESXCLOUD_DISK_OVA_IMAGE is not defined")
          EsxCloud::Image.create(image_file, random_name("image-"), "EAGER")
        end

        it "should delete its image in PENDING_DELETE" do
          image_id = image.id
          vm1 = create_vm(@project, image_id: image.id)
          vm2 = create_vm(@project, image_id: image.id)
          image.delete
          image = EsxCloud::Image.find_by_id(image_id)
          expect(image).to_not be_nil
          expect(image.state).to eq "PENDING_DELETE"

          vm1.delete
          image = EsxCloud::Image.find_by_id(image_id)
          expect(image).to_not be_nil
          expect(image.state).to eq "PENDING_DELETE"

          vm2.delete
          image = EsxCloud::Image.find_all.items.find { |i| i.id == image_id }
          expect(image).to be_nil
        end
      end
    end
  end

  describe "#iso_operations", iso_file: true, real_agent: true do
    let(:iso_path) { ENV["ESXCLOUD_ISO_FILE"] || fail("ESXCLOUD_ISO_FILE is not defined") }
    let(:iso_name) { "iso-name" }
    let(:vm) do
      create_vm(@project)
    end

    after(:each) do
      vm.delete
    end

    it "should attach and detach one ISO" do
      vm.attach_iso(iso_path, iso_name)
      vm.isos.size.should == 1
      vm.isos[0].name.should == iso_name
      vm.detach_iso
    end

    it "fails detaching iso when no ISO is attached" do
      begin
        vm.detach_iso
        fail("should fail detaching iso when no ISO is attached")
      rescue EsxCloud::ApiError => e
        e.response_code.should == 200
        e.errors.size.should == 1
        e.errors[0][0].code.should include("NoIsoAttached")
      rescue EsxCloud::CliError => e
        e.output.should match("NoIsoAttached")
      end
    end

    context "when ISO is already attached" do
      before do
        vm.attach_iso(iso_path, "iso-name-1")
      end

      it "should complain when attach two ISOs" do
        begin
          vm.attach_iso(iso_path, "iso-name-2")
          fail("should fail when attaching a second ISO")
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should include("IsoAlreadyAttached")
        rescue EsxCloud::CliError => e
          e.output.should match("IsoAlreadyAttached")
        end
      end
    end
  end

  context "failure at agent call" do
    it "should fail requesting too much memory" do
      tenant = create_random_tenant
      resource_ticket = tenant.create_resource_ticket(
        name: random_name("rt-"),
        limits: [create_limit("vm.memory", 9000.0, "GB")])
      project = tenant.create_project(
        name: random_name("project-"),
        resource_ticket_name: resource_ticket.name,
        limits: [create_subdivide_limit(100.0)]
      )

      vm_name = "vm-1"
      begin
        create_vm(project, name: vm_name, flavor: @seeder.huge_vm_flavor!.name)
      rescue EsxCloud::ApiError => e
        e.response_code.should == 200
        e.errors.size.should == 1
        e.errors.first.size.should == 1
        step_error = e.errors.first.first
        step_error.code.should == "NotEnoughMemoryResource"
        step_error.step["operation"].should == "RESERVE_RESOURCE"
      rescue EsxCloud::CliError => e
        e.output.should match("NotEnoughMemoryResource")
      end

      vms = client.find_all_vms(project.id).items
      vms.size.should == 1
      vm = vms.first
      vm.flavor.should == @seeder.huge_vm_flavor!.name
      vm.name.should == vm_name
      vm.state.should == "ERROR"

      vm.delete
      @cleaner.delete_tenant(tenant)
    end

    it "should complain when disk size exceeds the capacity" do
      edisk_flavor = @seeder.ephemeral_disk_flavor!
      boot_disk = EsxCloud::VmDisk.new(random_name("disk-"), "ephemeral-disk", edisk_flavor.name, nil, true)
      big_disk = EsxCloud::VmDisk.new(random_name("disk-"), "ephemeral-disk", edisk_flavor.name, 10000000, false)
      vm_name = random_name("vm-")

      begin
        create_vm(@project, name: vm_name, disks: [boot_disk, big_disk])
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

      vms = client.find_vms_by_name(@project.id, vm_name).items
      vms.size.should == 1
      vms.first.state.should == "ERROR"
    end

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

  def is_pingable?(addr)
    ping_count = 4
    system("ping -q -c #{ping_count} #{addr}")
    $?.exitstatus == 0
  end

  def is_ssh_open?(addr)
    username = ENV["VM_IMAGE_USERNAME"] || "root"
    password = ENV["VM_IMAGE_PASSWORD"] || "root"
    Net::SSH.start(addr,
                   username,
                   password: password,
                   paranoid: false,
                   timeout: 10) do |session|
      session.exec!("ls -l") != nil
    end
  end

end
