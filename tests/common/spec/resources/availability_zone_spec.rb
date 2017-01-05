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

describe EsxCloud::AvailabilityZone do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "delegates create to API client" do
    spec = EsxCloud::AvailabilityZoneCreateSpec.new("name")
    spec_hash = { :name => "name"}

    expect(@client).to receive(:create_availability_zone).with(spec_hash)
    EsxCloud::AvailabilityZone.create(spec)
  end

  it "delegates delete to API client" do
    expect(@client).to receive(:delete_availability_zone).with("foo")
    EsxCloud::AvailabilityZone.delete("foo")
  end

  it "delegates find_by_id to API client" do
    expect(@client).to receive(:find_availability_zone_by_id).with("foo")
    EsxCloud::AvailabilityZone.find_by_id("foo")
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "zone-name",
        "kind" => "availability-zone",
        "state" => "READY"
    }
    from_hash = EsxCloud::AvailabilityZone.create_from_hash(hash)
    from_json = EsxCloud::AvailabilityZone.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |availability_zone|
      availability_zone.id.should == "foo"
      availability_zone.name.should == "zone-name"
      availability_zone.kind.should == "availability-zone"
      availability_zone.state.should == "READY"
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::AvailabilityZone.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

end
