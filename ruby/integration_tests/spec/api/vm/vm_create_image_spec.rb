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

describe "vm#create_image", management: true, image: true do
  let(:replication_type) { "ON_DEMAND" }
  let(:image_name) { random_name("image-") }
  let(:image_create_spec) { EsxCloud::ImageCreateSpec.new(image_name, replication_type)  }
  let(:default_network) { EsxCloud::SystemSeeder.instance.network! }
  let(:vm) { EsxCloud::SystemSeeder.instance.vm! }
  let(:vm_id) { vm.id }
  let(:image) { EsxCloud::Image.create_from_vm(vm_id, image_create_spec) }

  after(:each) do
    image_list = EsxCloud::Image.find_all.items.select { |i| i.name == image_name }
    image_list.each { |i| ignoring_all_errors { i.delete } }
  end

  after(:all) do
    ignoring_all_errors { default_network.delete }
  end

  context "when parameters are valid" do
    context "when replication type is ON_DEMAND" do
      it "creates the image" do
        expect(image.size).to be EsxCloud::SystemSeeder.instance.image!.size
        expect(image.name).to eq image_create_spec.name
        expect(image.state).to eq "READY"
        expect(image.replication).to eq replication_type

        # verify image can be used to create vm
        verify_image_cloned_from_vm
      end
    end

    context "when replication type is EAGER" do
      let(:replication_type) { "EAGER" }

      it "creates the image" do
        expect(image.size).to be EsxCloud::SystemSeeder.instance.image!.size
        expect(image.name).to eq image_create_spec.name
        expect(image.state).to eq "READY"
        expect(image.replication).to eq replication_type

        # verify image can be used to create vm
        verify_image_cloned_from_vm
      end
    end

    private

    def verify_image_cloned_from_vm
      new_vm = create_vm(EsxCloud::SystemSeeder.instance.project, image_id: image.id)
      expect(new_vm.state).to eq "STOPPED"
      new_vm.start!
      expect(new_vm.state).to eq "STARTED"
      new_vm.stop!
      expect(new_vm.state).to eq "STOPPED"
    ensure
      return unless new_vm
      ignoring_all_errors do
        new_vm.stop! if new_vm.state == "STARTED"
        new_vm.delete
      end
    end
  end

  context "when VM is powered on" do
    let(:vm) do
      vm = create_vm(EsxCloud::SystemSeeder.instance.project)
      vm.start!
      vm
    end

    after(:each) do
      ignoring_all_errors do
        vm.stop! if vm.state == "STARTED"
        vm.delete
      end
    end

    it "fails to create image" do
      begin
        image
        fail "create image from powered on VM should have failed"
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 200
        expect(e.errors.size).to eq 1
        expect(e.errors.first.first.code).to eq "InvalidVmState"
      rescue EsxCloud::CliError => e
        expect(e.message).to match("InvalidVmState")
      end
    end
  end

  context "when vm does not exist" do
    let(:vm_id) { "non-existing-vm" }
    it "fails to create image" do
      begin
        image
        fail "create image from non-existent VM should have failed"
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 404
        expect(e.errors.size).to eq 1
        expect(e.errors.first.code).to eq "VmNotFound"
      rescue EsxCloud::CliError => e
        expect(e.message).to match("VmNotFound")
      end
    end
  end

  context "when image name is not provided" do
    let(:image_name) { nil }
    it "fails to create image" do
      begin
        image
        fail "Image name is required"
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 400
        expect(e.errors.size).to eq 1
        expect(e.errors.first.code).to eq "InvalidEntity"
        expect(e.errors.first.message).to match("name may not be null")
      rescue EsxCloud::CliError => e
        expect(e.message).to match("name size must be between 1 and 63")
      end
    end
  end

  context "when image replication type is not provided" do
    let(:replication_type) { nil }
    it "fails to create image" do
      begin
        image
        fail "Image replication type is required"
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 400
        expect(e.errors.size).to eq 1
        expect(e.errors.first.code).to eq "InvalidEntity"
        expect(e.errors.first.message).to match("replicationType may not be null")
      rescue EsxCloud::CliError => e
        expect(e.message).to include("was not one of [ON_DEMAND, EAGER]")
      end
    end
  end

  context "when image replication type is invalid" do
    let(:replication_type) { "INVALID_TYPE" }
    it "fails to create image" do
      begin
        image
        fail "Image replication type is invalid"
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 400
        expect(e.errors.size).to eq 1
        expect(e.errors.first.code).to eq "InvalidJson"
        expect(e.errors.first.message).to include("INVALID_TYPE was not one of [ON_DEMAND, EAGER]")
      rescue EsxCloud::CliError => e
        expect(e.message).to include("INVALID_TYPE was not one of [ON_DEMAND, EAGER]")
      end
    end
  end
end
