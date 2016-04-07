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

describe "availability_zone" do
  let(:availabilityZones_to_delete) { [] }

  after(:each) do
    availabilityZones_to_delete.each do |availabilityZone|
      availabilityZone.delete unless availabilityZone.nil?
    end
  end

  it "should create one, get it, and then delete it" do
    availability_zone_name = random_name("availability-zone-")
    availability_zone = create_availabilityZone(availability_zone_name)
    availability_zone = find_availability_zone_by_id(availability_zone.id)
    expect(availability_zone.name).to eq(availability_zone_name)
    expect(availability_zone.state).to eq("READY")

    tasks = client.get_availability_zone_tasks(availability_zone.id).items
    expect(tasks.size).to eq(1)
    expect(tasks.first.operation).to eq("CREATE_AVAILABILITYZONE")
    expect(tasks.first.state).to eq("COMPLETED")

    availability_zone.delete

    availability_zone = find_availability_zone_by_id(availability_zone.id)
    expect(availability_zone.name).to eq(availability_zone_name)
    expect(availability_zone.state).to eq("PENDING_DELETE")
  end

  it "should raise exception for undefined availability_zone" do
    availability_zone_id = "fake-availability-zone-id"
    begin
      find_availability_zone_by_id(availability_zone_id)
      fail("Find availability_zone with id '#{availability_zone_id}' should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 404
      e.errors.size.should == 1
      e.errors[0].code.should include("AvailabilityZoneNotFound")
    end
  end

  it "should raise exception for deleting non existed availability_zone" do
    availability_zone_id = random_name("fake-availability-zone-id-")
    begin
      delete_availability_zone_by_id(availability_zone_id)
      fail("AvailabilityZone delete with id #{availability_zone_id} should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 404
      e.errors.size.should == 1
      e.errors[0].code.should include("AvailabilityZoneNotFound")
    end
  end

  it "should create two availability zones with the same name" do
    availability_zone_name = random_name("availability-zone-")

    availability_zone1 = create_availabilityZone(availability_zone_name)
    expect(availability_zone1.name).to eq(availability_zone_name)
    expect(availability_zone1.state).to eq("READY")

    availability_zone2 = create_availabilityZone(availability_zone_name)
    expect(availability_zone2.name).to eq(availability_zone_name)
    expect(availability_zone2.state).to eq("READY")

    availability_zones = find_all_availability_zones().items.select { |az| az.name == availability_zone_name }
    availability_zones.size.should == 2
    expect(availability_zones[0].id).not_to eq(availability_zones[1].id)

  end

  xit "should list multiple availability_zones" do
    availability_zones = find_all_availability_zones()
    availability_zones.items.size.should == 0

    availability_zone_name = random_name("availability-zone-")
    availability_zone = create_availabilityZone(availability_zone_name)

    tasks = client.get_availability_zone_tasks(availability_zone.id).items
    expect(tasks.size).to eq(1)
    expect(tasks.first.operation).to eq("CREATE_AVAILABILITYZONE")
    expect(tasks.first.state).to eq("COMPLETED")

    availability_zones = find_all_availability_zones()
    availability_zones.items.size.should == 1
    availability_zones.items[0].name.should == availability_zone_name

    availability_zone_name = random_name("availability-zone-")
    availability_zone = create_availabilityZone(availability_zone_name)

    tasks = client.get_availability_zone_tasks(availability_zone.id).items
    expect(tasks.size).to eq(1)
    expect(tasks.first.operation).to eq("CREATE_AVAILABILITYZONE")
    expect(tasks.first.state).to eq("COMPLETED")

    availability_zones = find_all_availability_zones()
    availability_zones.items.size.should == 2
  end

  private

  def create_availabilityZone(name)
    begin
      availabilityZone = create_availability_zone(EsxCloud::AvailabilityZoneCreateSpec.new(name))
      availabilityZones_to_delete << availabilityZone
      availabilityZone
    rescue
      availabilityZones_to_delete << EsxCloud::AvailabilityZone.find_all.items.find_all { |az| az.name == name }
      raise
    end
  end
end
