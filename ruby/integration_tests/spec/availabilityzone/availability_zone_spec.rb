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
require_relative "../../lib/dcp/cloud_store/host_factory"

describe "Availability Zone", availabilityzone: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @cleaner = EsxCloud::SystemCleaner.new(client)

    # seed the image on all image datastores
    @seeder.image!
    wait_for_image_seeding_progress_is_done
  end

  it "set host's availability zone and create VMs successfully" do
    # create an availability zone
    availability_zone_name = random_name("zone3-")
    availability_zone = create_availability_zone(EsxCloud::AvailabilityZoneCreateSpec.new(availability_zone_name))

    deployment = client.find_all_api_deployments.items.first
    expect(deployment).to_not be_nil

    host = client.get_deployment_hosts(deployment.id).items.select { |h| h.availability_zone.nil? and h.usage_tags.include? "CLOUD" }.first
    fail "No Host found without availability zone. Hence cannot proceed." if host.nil?

    # set availability zone
    setAvailabilityZoneSpec = EsxCloud::HostSetAvailabilityZoneSpec.new(availability_zone.id)
    host = host_set_availability_zone(host.id, setAvailabilityZoneSpec)
    expect(host).to_not be_nil
    expect(host.address).to_not be_nil

      # create VMs in specific availability zone
      for i in 1..3
        vm_name = random_name("vm-#{i}-")
        begin
          vm = create_vm(@seeder.project!, name: vm_name, affinities: [{id: availability_zone.id, kind: "availabilityZone"}])
        rescue EsxCloud::Error => e
          host_service = EsxCloud::Dcp::CloudStore::HostFactory.get_host host.id
          fail "Create VM failed. Host: #{host.id} HostState: #{host_service["agentState"]} Error: #{e}"
        end

        expect(vm).to_not be_nil
        expect(vm.name).to eq(vm_name)
        expect(vm.host).to eq(host.address)

        vm.delete
      end
  end

  it "set host's availability zone again should fail" do
    # create an availability zone
    availability_zone_name = random_name("zone3-")
    availability_zone = create_availability_zone(EsxCloud::AvailabilityZoneCreateSpec.new(availability_zone_name))

    deployment = client.find_all_api_deployments.items.first
    expect(deployment).to_not be_nil

    host = client.get_deployment_hosts(deployment.id).items.select { |h| !h.availability_zone.nil? }.first
    fail "No Host found with availability zone. Hence cannot proceed." if host.nil?

    setAvailabilityZoneSpec = EsxCloud::HostSetAvailabilityZoneSpec.new(availability_zone.id)
    error_message = "Host #{host.id} is already part of Availability Zone #{host.availability_zone}"
    begin
      host = host_set_availability_zone(host.id, setAvailabilityZoneSpec)
      fail("There should be an error when resetting host's availability zone.")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "HostAvailabilityZoneAlreadySet"
      e.errors[0].message.should == error_message
    rescue EsxCloud::CliError => e
      e.output.should match(error_message)
    end
  end

end
