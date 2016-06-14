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

  let(:client) do
    cmd = "/path/to/cli target set --nocertcheck localhost:9000"
    expect(EsxCloud::CmdRunner).to receive(:run).with(cmd)
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  end

  describe "#create_virtual_network" do
    it "delegates to api client" do
      network = double("network")
      expect(@api_client).to receive(:create_virtual_network).with("p1", {}).and_return(network)

      expect(client.create_virtual_network("p1", {})).to be(network)
    end
  end

  describe "#delete_virtual_network" do
    it "delegates to api client" do
      expect(@api_client).to receive(:delete_virtual_network).with("n1").and_return(true)

      expect(client.delete_virtual_network("n1")).to be_true
    end
  end

  describe "#find_virtual_network_by_id" do
    it "delegates to api client" do
      network = double("network")
      expect(@api_client).to receive(:find_virtual_network_by_id).with("n1").and_return(network)

      expect(client.find_virtual_network_by_id("n1")).to be(network)
    end
  end

  describe "#find_virtual_network_by_name" do
    it "delegates to api client" do
      networks = double("networks")
      expect(@api_client).to receive(:find_virtual_networks_by_name).with("name").and_return(networks)

      expect(client.find_virtual_networks_by_name("name")).to be(networks)
    end
  end
end
