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

describe EsxCloud::Network do

  let(:client) { double(EsxCloud::ApiClient) }
  let(:network) { EsxCloud::Network.new "id", "name", "description", "READY", ["P1"] }
  let(:network_list) { EsxCloud::NetworkList.new [network] }

  let(:create_spec) { EsxCloud::NetworkCreateSpec.new("network1", "VLAN", ["P1", "P2"]) }

  before(:each) do
    allow(EsxCloud::Config).to receive(:client).and_return(client)
  end

  it "delegates create to client" do
    expect(client).to receive(:create_network).with(create_spec.to_hash).and_return(network)
    expect(EsxCloud::Network.create(create_spec)).to eq network
  end

  it "delegates delete to API client" do
    expect(client).to receive(:delete_network).with("id")
    EsxCloud::Network.delete("id")
  end

  it "delegates find_by_id to client" do
    expect(client).to receive(:find_network_by_id).with("id").and_return(network)
    expect(EsxCloud::Network.find_network_by_id("id")).to eq network
  end

  it "delegates find_all to client" do
    expect(client).to receive(:find_all_networks).and_return(network_list)
    expect(EsxCloud::Network.find_all).to eq network_list
  end

  it "delegates find_by_name to client" do
    expect(client).to receive(:find_networks_by_name).with("name").and_return(network_list)
    expect(EsxCloud::Network.find_by_name("name")).to eq network_list
  end

  it "delegates deleted to client" do
    expect(client).to receive(:delete_network).with("id")
    network.delete
  end
end
