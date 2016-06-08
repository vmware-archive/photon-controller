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

describe EsxCloud::CliClient do

  let(:api_client) { double(EsxCloud::ApiClient) }
  let(:client) { EsxCloud::CliClient.new("/path/to/cli", "localhost:9000") }

  before(:each) do
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  it "creates a network" do
    network = double(EsxCloud::Network, id: "n1")
    networks = double(EsxCloud::NetworkList, items: [network])

    spec = EsxCloud::NetworkCreateSpec.new("network1", "VLAN", ["P1", "P2"])

    expect(client).to receive(:run_cli)
                      .with("network create -n 'network1' -p 'P1, P2' -d 'VLAN'")
    expect(client).to receive(:find_networks_by_name).with("network1").and_return(networks)

    expect(client.create_network(spec.to_hash)).to eq network
  end

  it "deletes a network" do
    expect(client).to receive(:run_cli).with("network delete 'foo'")

    client.delete_network("foo").should be_true
  end

  it "sets port groups" do
    portgroups = ["PG1", "PG2"]
    network = double(EsxCloud::Network)

    expect(client).to receive(:run_cli)
                           .with("network set_portgroups network-id -p 'PG1, PG2'")
    expect(api_client).to receive(:find_network_by_id).with("network-id").and_return(network)

    expect(client.set_portgroups("network-id", portgroups)).to eq network
  end

  it "sets default network" do
    expect(client).to receive(:run_cli)
                      .with("network set_default network-id")

    expect(client.set_default("network-id")).to be_true
  end

  it "finds network by id" do
    network = double(EsxCloud::Network)

    expect(api_client).to receive(:find_network_by_id).with("n1")
                           .and_return(network)
    expect(client.find_network_by_id("n1")).to eq network
  end

  it "finds networks by name" do
    networks = double(EsxCloud::NetworkList)

    expect(api_client).to receive(:find_networks_by_name).with("n1")
                           .and_return(networks)
    expect(client.find_networks_by_name("n1")).to eq networks
  end

  it "finds all networks" do
    networks = double(EsxCloud::NetworkList)

    expect(api_client).to receive(:find_all_networks).and_return(networks)
    expect(client.find_all_networks).to eq networks
  end
end
