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

  it "creates a network" do
    network = double(EsxCloud::Network)

    expect(http_client).to receive(:post_json)
                            .with("/subnets", "payload")
                            .and_return(task_created("aaa"))

    expect(http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "network-id"))
    expect(http_client).to receive(:get).with("/subnets/network-id").and_return(ok_response("network"))

    expect(client.create_network("payload")).to eq network
  end

  it "deletes a network" do
    expect(http_client).to receive(:delete).with("/subnets/foo").and_return(task_created("aaa"))
    expect(http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    expect(client.delete_network("foo")).to be_true
  end

  it "sets port groups" do
    portgroups = ["PG1", "PG2"]
    network = double(EsxCloud::Network)

    expect(http_client).to receive(:post_json)
                           .with("/subnets/network-id/set_portgroups", {items: portgroups})
                           .and_return(task_created("aaa"))
    expect(http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "network-id"))
    expect(http_client).to receive(:get).with("/subnets/network-id").and_return(ok_response("network"))

    expect(client.set_portgroups("network-id", portgroups)).to eq network
  end

  it "sets default network" do
    expect(http_client).to receive(:post)
                           .with("/subnets/network-id/set_default")
                           .and_return(task_created("aaa"))
    expect(http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "network-id"))

    expect(client.set_default("network-id")).to be_true
  end

  it "finds network by id" do
    network = double(EsxCloud::Network)

    expect(http_client).to receive(:get).with("/subnets/n1")
                                 .and_return(network)

    expect(client.find_network_by_id("n1")).to eq network
  end

  it "finds networks by name" do
    networks = double(EsxCloud::NetworkList)

    expect(http_client).to receive(:get).with("/subnets?name=n1")
                            .and_return(ok_response("networks"))
    expect(EsxCloud::NetworkList).to receive(:create_from_json).with("networks")
                                     .and_return(networks)

    expect(client.find_networks_by_name("n1")).to eq networks
  end

  it "finds all networks" do
    networks = double(EsxCloud::NetworkList)

    expect(http_client).to receive(:get).with("/subnets").and_return(ok_response("networks"))
    expect(EsxCloud::NetworkList).to receive(:create_from_json).with("networks").and_return(networks)

    expect(client.find_all_networks).to eq networks
  end

end
