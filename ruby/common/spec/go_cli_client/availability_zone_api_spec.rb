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

describe EsxCloud::GoCliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    cmd = "/path/to/cli target set --nocertcheck localhost:9000"
    expect(EsxCloud::CmdRunner).to receive(:run).with(cmd)
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  }

  it "creates availability zone" do
    availability_zone = double(EsxCloud::AvailabilityZone)
    spec = { :name => "availabilityzone1"}
    payload = {name: "availabilityzone1"}
    expect(@api_client).to receive(:create_availability_zone).with(payload).and_return(availability_zone)
    client.create_availability_zone(spec).should == availability_zone
  end

  it "finds availability zone by id" do
    availability_zone = double(EsxCloud::AvailabilityZone)
    expect(@api_client).to receive(:find_availability_zone_by_id).with("foo").and_return(availability_zone)
    client.find_availability_zone_by_id("foo").should == availability_zone
  end

  it "finds all availability zones" do
    availability_zones = double(EsxCloud::AvailabilityZoneList)
    expect(@api_client).to receive(:find_all_availability_zones).and_return(availability_zones)
    client.find_all_availability_zones.should == availability_zones
  end

  it "deletes availability zone by id" do
    expect(@api_client).to receive(:delete_availability_zone).with("foo").and_return(true)
    client.delete_availability_zone("foo").should be_true
  end

  it "gets availability zone tasks" do
    tasks = double(EsxCloud::TaskList)
    expect(@api_client).to receive(:get_availability_zone_tasks).with("foo", "a").and_return(tasks)
    client.get_availability_zone_tasks("foo", "a").should == tasks
  end
end
