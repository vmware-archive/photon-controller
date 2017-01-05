# Copyright 2016 VMware, Inc. All Rights Reserved.
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

  it "creates a network" do
    network_id = double("n1")
    network = double(EsxCloud::Network, id: network_id)

    spec = EsxCloud::NetworkCreateSpec.new("network1", "VLAN", ["P1", "P2"])

    expect(client).to receive(:run_cli)
                      .with("network create -n 'network1' -p 'P1, P2' -d 'VLAN'")
                      .and_return(network_id)
    expect(client).to receive(:find_network_by_id).with(network_id).and_return(network)

    expect(client.create_network(spec.to_hash)).to eq network
  end

  it "deletes a network" do
    expect(client).to receive(:run_cli).with("network delete 'foo'")

    client.delete_network("foo").should be_true
  end

  it "sets port groups" do
    portgroups = ["PG1", "PG2"]
    network_id = double("n1")
    network = double(EsxCloud::Network)

    expect(@api_client).to receive(:set_portgroups).with(network_id, portgroups).and_return(network)

    expect(client.set_portgroups(network_id, portgroups)).to eq network
  end

  it "sets default network" do
    expect(client).to receive(:run_cli).with("network set-default 'network1'")
    expect(client.set_default("network1")).to be_true
  end

  it "finds network by id" do
    network_id = double("n1", id: network_id)
    network_hash ={"id" => network_id,
                   "name" => "network1",
                   "state" => "READY",
                   "portGroups" => ["P1", "P2"],
                   "description" => "VLAN",
                   "isDefault" => false }
    network = EsxCloud::Network.create_from_hash(network_hash)

    result = "#{network_id} network1  READY P1,P2 VLAN false"
    expect(client).to receive(:run_cli).with("network show #{network_id}").and_return(result)
    expect(client).to receive(:get_network_from_response).with(result).and_return(network)
    expect(client.find_network_by_id(network_id)).to eq network
  end

  it "finds networks by name" do
    networks = double(EsxCloud::NetworkList)

    expect(@api_client).to receive(:find_networks_by_name).with("n1").and_return(networks)
    expect(client.find_networks_by_name("n1")).to eq networks
  end

  it "finds all networks" do
    networks = double(EsxCloud::NetworkList)
    result ="n1 network1  READY P1,P2 VLAN
             n2 network2  READY P1,P2 VLAN"
    expect(client).to receive(:run_cli).with("network list").and_return(result)
    expect(client).to receive(:get_network_list_from_response).with(result).and_return(networks)
    expect(client.find_all_networks).to eq networks
  end
end
