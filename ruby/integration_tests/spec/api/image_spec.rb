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
require 'tempfile'
require 'tmpdir'

describe "image", management: true, image: true do
  let(:images_to_delete) { [] }
  let(:test_image) { ENV["ESXCLOUD_DISK_OVA_IMAGE"] }
  let(:image_name) { random_name("image-") }
  let(:project) { @seeder.project! }
  let(:vm_flavor) { @seeder.vm_flavor! }
  let(:huge_vm_flavor) { @seeder.huge_vm_flavor! }

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new([create_limit("vm.memory", 15000.0, "GB")])
    @cleaner = EsxCloud::SystemCleaner.new(client)
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
  end

  after(:each) do
    images_to_delete.each do |image|
      image.delete unless image.nil?
    end
  end

  subject do
    create_image(test_image, image_name)
  end

  context "when image is in OVA format" do
    ["EAGER", "ON_DEMAND"].each do |replication|
      it "uploads image with #{replication} replication option" do
        image_upload = create_image(test_image, image_name, replication)

        Dir.mktmpdir do |ovf_folder|
          system("tar -xf #{test_image} -C #{ovf_folder}")
          ovf_files = Dir.glob("#{ovf_folder}/*")
          vmdk_file = ovf_files.find { |file| file.include?(".vmdk") }

          expect(image_upload.size).to be >= File.stat(vmdk_file).size
        end

        image_upload.name.should == image_name
        image_upload.state.should == "READY"
        image_upload.replication.should == replication
        image_upload.settings.size.should == 1
        image_upload.settings[0]['name'].should == "scsi0.virtualDev"

        image_upload.settings[0]['defaultValue'].should == "lsilogic"

        # image shows up in the list
        image_list = EsxCloud::Image.find_all
        expect(image_list.items).to_not be_empty
        uploaded_images = image_list.items.select { |i| i.id == image_upload.id}
        expect(uploaded_images.size).to eq 1

        # One task is associated with this image
        tasks = client.get_image_tasks(image_upload.id).items
        expect(tasks.size).to eq(1)
        expect([tasks.first.operation, tasks.first.state]).to eq(["CREATE_IMAGE", "COMPLETED"])
      end
    end

    it "should allow duplicated name" do
      # create one image
      expect(subject.name).to eq image_name
      expect(subject.state).to eq "READY"
      expect(subject.replication).to eq "EAGER"

      # One task is associated with the first image
      tasks = client.get_image_tasks(subject.id).items
      expect(tasks.size).to eq(1)
      expect([tasks.first.operation, tasks.first.state]).to eq(["CREATE_IMAGE", "COMPLETED"])

      # create second image
      image2 = create_image(test_image, image_name)
      expect(image2.name).to eq image_name
      expect(image2.state).to eq "READY"
      expect(image2.replication).to eq "EAGER"

      # One task is associated with the second image
      tasks = client.get_image_tasks(image2.id).items
      expect(tasks.size).to eq(1)
      expect([tasks.first.operation, tasks.first.state]).to eq(["CREATE_IMAGE", "COMPLETED"])

      image_list = EsxCloud::Image.find_by_name(image_name)
      expect(image_list.items.size).to eq 2
    end

    context "when image disk is not stream optimized" do
      let(:test_image) { ENV["ESXCLOUD_BAD_DISK_IMAGE"] }

      it "fails with VmdkFormatException", real_agent: true do
        begin
          create_image(test_image, image_name)
          fail("Creating image should have failed due to InvalidVmdkFormat")
        rescue EsxCloud::ApiError => e
          e.response_code.should == 200
          e.errors.size.should == 1
          e.errors[0].to_s.should match("InvalidVmdkFormat")
        rescue EsxCloud::CliError => e
          e.message.should match("InvalidVmdkFormat")
        end
      end
    end

    context "when image replication type is not provided" do
      it "fails with ImageUploadError" do
        begin
          EsxCloud::Image.create(test_image, image_name, nil)
          fail "Image replication type is required"
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "ImageUploadError"
        rescue EsxCloud::CliError => e
          e.message.should match("ImageUploadError")
        end
      end
    end

    context "when image replication type is invalid" do
      it "fails with ImageUploadError caused by invalid parameter" do
        begin
          EsxCloud::Image.create(test_image, image_name, "Invalid")
          fail "Image replication type is invalid"
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "ImageUploadError"
          e.errors[0].message.should match("Image upload receives invalid parameter")
        rescue EsxCloud::CliError => e
          e.message.should match("Image upload receives invalid parameter")
        end
      end
    end
  end

  context "when image is in VMDK format" do
    let(:test_image) { ENV["ESXCLOUD_DISK_IMAGE"] }

    it "creates the image" do
      expect(subject.size).to be >= File.stat(test_image).size
      expect(subject.name).to eq image_name
      expect(subject.state).to eq "READY"

      # image can be found by id
      expect(EsxCloud::Image.find_by_id(subject.id)).to eq subject

      # image shows up in image list
      image_list = EsxCloud::Image.find_all
      expect(image_list.items).to_not be_empty
      filtered_images = image_list.items.select { |i| i.id == subject.id}
      expect(filtered_images.size).to eq 1

      # One task is associated with this image
      tasks = client.get_image_tasks(subject.id).items
      expect(tasks.size).to eq(1)
      expect([tasks.first.operation, tasks.first.state]).to eq(["CREATE_IMAGE", "COMPLETED"])
    end
  end

  context "when image is not OVA or VMDK format" do
    let(:test_image) { @seeder.create_dummy_image_file }
    after(:each) do
      File.delete(test_image) if File.exist?(test_image)
    end

    it "fails to upload" do
      begin
        create_image(test_image, image_name)
        fail "Create image that is not OVA or VMDK format should have failed"
      rescue EsxCloud::ApiError => e
        e.response_code.should == 200
        e.errors.size.should == 1
        e.errors.first.size.should == 1
        step_error = e.errors.first.first
        step_error.code.should == "UnsupportedImageFileType"
        step_error.message.should == "Image must be either a vmdk or an ova file."
        step_error.step["operation"].should == "UPLOAD_IMAGE"
      rescue EsxCloud::CliError => e
        e.message.should match("Image must be either a vmdk or an ova file.")
      end
    end
  end

  context "image is PENDING_DELETE" do
    let(:image_id) do
      image = EsxCloud::Image.create(test_image, image_name, "EAGER")
      image.id
    end
    let(:network) do
      network = @seeder.network!
    end
    let(:vm) do
      project.create_vm(
        name: random_name("vm-"), flavor: vm_flavor.name,
        image_id: image_id, disks: create_ephemeral_disks([random_name("disk-")]),
        networks: [network.id])
    end

    before do
      network # call let(:network) for network to create vm
      vm # call let(:vm) for vm to use image
      image = EsxCloud::Image.find_by_id(image_id)
      image.delete
    end

    after do
      network.delete
      vm.delete
    end

    it "should list/show image" do
      image = EsxCloud::Image.find_by_id(image_id)
      expect(image.state).to eq "PENDING_DELETE"

      image = EsxCloud::Image.find_all.items.find { |i| i.id == image_id }
      expect(image).to_not be_nil
      expect(image.state).to eq "PENDING_DELETE"
      expect(image.replication_progress =~ /^(100|\d{1,2})%$/).to_not be nil
      expect(image.seeding_progress =~ /^(100|\d{1,2})%$/).to_not be nil

      tasks = client.get_image_tasks(image.id).items
      expect(tasks.size).to eq(2)
      expect(tasks.map{|x| [x.operation, x.state]} - [["CREATE_IMAGE", "COMPLETED"], ["DELETE_IMAGE", "COMPLETED"]])
          .to be_empty
    end
  end

  context "when image is in use" do
    subject do
      EsxCloud::Image.create(test_image, image_name, "EAGER")
    end

    let(:vm) do
      project.create_vm(
        name: random_name("vm-"), flavor: vm_flavor.name,
        image_id: subject.id, disks: create_ephemeral_disks([random_name("disk-")]))
    end

    after do
      vm.resume! if vm.state == "SUSPENDED"
      vm.stop! if vm.state == "STARTED"
      vm.delete
    end

    shared_examples "image deletion" do
      it "moves image to PENDING_DELETE" do
        expect(image.state).to eq "READY"

        image_id = image.id
        image.delete

        image = EsxCloud::Image.find_by_id(image_id)
        expect(image.state).to eq "PENDING_DELETE"

        image = EsxCloud::Image.find_by_id(image_id)
        begin
          image.delete
          fail "Delete image in PENDING_DELETE state should have failed"
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "InvalidImageState"
        rescue EsxCloud::CliError => e
          e.message.should match("InvalidImageState")
        end

        image = EsxCloud::Image.find_by_id(image_id)
        expect(image.state).to eq "PENDING_DELETE"

        tasks = client.get_image_tasks(image.id).items
        expect(tasks.size).to eq(2)
        expect(tasks.map{|x| [x.operation, x.state]} - [["CREATE_IMAGE", "COMPLETED"], ["DELETE_IMAGE", "COMPLETED"]])
            .to be_empty
      end
    end

    context "vm is STOPPED" do
      before do
        vm # call let(:vm) for vm to use image
      end

      it_behaves_like "image deletion" do
        let(:image) { subject }
      end
    end

    context "vm is STARTED" do
      before do
        vm.start!
      end

      it_behaves_like "image deletion" do
        let(:image) { subject }
      end
    end

    context "vm is SUSPENDED" do
      before do
        vm.start!
        vm.suspend!
      end

      it_behaves_like "image deletion" do
        let(:image) { subject }
      end
    end

    context "vm is ERROR" do
      let(:vm) do
        begin
          vm_name = random_name("vm-")
          project.create_vm(
            name: vm_name, flavor: huge_vm_flavor.name,
            image_id: subject.id, disks: create_ephemeral_disks([random_name("disk-")]))
          fail "create huge vm should have failed"
        rescue EsxCloud::ApiError, EsxCloud::CliError
          vms = client.find_vms_by_name(project.id, vm_name).items
          vms.size.should == 1
          vms.first
        end
      end

      before do
        vm.state.should == "ERROR" # call let(:vm) for vm to use image
      end

      it_behaves_like "image deletion" do
        let(:image) { subject }
      end
    end
  end

  private

  def create_image(file, name, replication = nil)
    if replication.nil?
      replication = "EAGER"
    end

    begin
      image = EsxCloud::Image.create(file, name, replication)
      images_to_delete << image
      image
    rescue
      EsxCloud::Image.find_by_name(image_name).items.each {|i| images_to_delete << i}
      raise
    end
  end
end
