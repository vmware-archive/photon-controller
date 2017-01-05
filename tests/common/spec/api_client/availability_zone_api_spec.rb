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

describe EsxCloud::ApiClient do

  before(:each) do
    @http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(@http_client)
  end

  let(:client) {
    EsxCloud::ApiClient.new("localhost:9000")
  }

  it "creates availability zone" do
    availability_zone = double(EsxCloud::AvailabilityZone)
    expect(@http_client).to receive(:post_json)
                            .with("/availabilityzones", "payload")
                            .and_return(task_created("aaa"))
    expect(@http_client).to receive(:get)
                            .with(URL_HOST + "/tasks/aaa")
                            .and_return(task_done("aaa", "availability-zone-id"))
    expect(@http_client).to receive(:get)
                            .with("/availabilityzones/availability-zone-id")
                            .and_return(ok_response("availability_zone"))
    expect(EsxCloud::AvailabilityZone).to receive(:create_from_json)
                                          .with("availability_zone")
                                          .and_return(availability_zone)

    client.create_availability_zone("payload").should == availability_zone
  end

  it "deletes availability zone" do
    expect(@http_client).to receive(:delete)
                            .with("/availabilityzones/foo")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_availability_zone("foo").should be_true
  end

  it "finds availability zone by id" do
    availability_zone = double(EsxCloud::AvailabilityZone)

    expect(@http_client).to receive(:get).with("/availabilityzones/foo").and_return(ok_response("availability-zone"))
    expect(EsxCloud::AvailabilityZone).to receive(:create_from_json)
                                          .with("availability-zone")
                                          .and_return(availability_zone)

    client.find_availability_zone_by_id("foo").should == availability_zone
  end

  it "finds all availability zones" do
    availability_zones = double(EsxCloud::AvailabilityZoneList)

    expect(@http_client).to receive(:get).with("/availabilityzones").and_return(ok_response("availability_zones"))
    expect(EsxCloud::AvailabilityZoneList).to receive(:create_from_json)
                                              .with("availability_zones")
                                              .and_return(availability_zones)

    client.find_all_availability_zones.should == availability_zones
  end

  it "gets availability zone tasks" do
    tasks = double(EsxCloud::TaskList)
    expect(@http_client).to receive(:get).with("/availabilityzones/foo/tasks").and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)
    client.get_availability_zone_tasks("foo").should == tasks
  end
end
