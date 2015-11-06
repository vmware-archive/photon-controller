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

  let(:client) { EsxCloud::ApiClient.new("localhost:9000") }
  let(:http_client) { double(EsxCloud::HttpClient) }

  before(:each) do
    EsxCloud::HttpClient.stub(:new).and_return(http_client)
  end

  it "finds portgroup by id" do
    portgroup = double(EsxCloud::PortGroup)

    expect(http_client).to receive(:get).with("/portgroups/p1")
                           .and_return(ok_response("portgroup"))
    expect(EsxCloud::PortGroup).to receive(:create_from_json).with("portgroup")
                                 .and_return(portgroup)

    expect(client.find_portgroup_by_id("p1")).to eq portgroup
  end

  it "finds all portgroups" do
    portgroups = double(EsxCloud::PortGroupList)

    expect(http_client).to receive(:get).with("/portgroups").and_return(ok_response("portgroups"))
    expect(EsxCloud::PortGroupList).to receive(:create_from_json).with("portgroups").and_return(portgroups)

    expect(client.find_portgroups(nil, nil)).to eq portgroups
  end

  it "finds portgroups by name" do
    portgroups = double(EsxCloud::PortGroupList)

    expect(http_client).to receive(:get).with("/portgroups?name=p1").and_return(ok_response("portgroups"))
    expect(EsxCloud::PortGroupList).to receive(:create_from_json).with("portgroups").and_return(portgroups)

    expect(client.find_portgroups("p1", nil)).to eq portgroups
  end

  it "finds portgroups by usage tags" do
    portgroups = double(EsxCloud::PortGroupList)

    expect(http_client).to receive(:get).with("/portgroups?usageTag=CLOUD").and_return(ok_response("portgroups"))
    expect(EsxCloud::PortGroupList).to receive(:create_from_json).with("portgroups").and_return(portgroups)

    expect(client.find_portgroups(nil, "CLOUD")).to eq portgroups
  end

  it "finds portgroups by both name and usage tags" do
    portgroups = double(EsxCloud::PortGroupList)

    expect(http_client).to receive(:get).with("/portgroups?name=p1&usageTag=CLOUD").and_return(ok_response("portgroups"))
    expect(EsxCloud::PortGroupList).to receive(:create_from_json).with("portgroups").and_return(portgroups)

    expect(client.find_portgroups("p1", "CLOUD")).to eq portgroups
  end
end
