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

describe EsxCloud::VirtualNetwork do

  let(:client) { double(EsxCloud::ApiClient) }
  let(:virtual_network) { EsxCloud::VirtualNetwork.new "id", "name", "description", "READY", "ROUTED", true, "cidr",
                                                       "lowIpDynamic", "highIpDynamic", "lowIpStatic", "highIpStatic",
                                                       ["reserved_ip1", "reserved_ip2"]}
  let(:virtual_network_list) { EsxCloud::VirtualNetworkList.new [virtual_network] }

  let(:create_spec) { EsxCloud::VirtualNetworkCreateSpec.new("virtual_network", "virtual_network_desc", "ROUTED",
                                                             128, 8)}
  let(:project_id) {"project_id"}

  before(:each) do
    allow(EsxCloud::Config).to receive(:client).and_return(client)
  end

  it "delegates create to client" do
    expect(client).to receive(:create_virtual_network).with(project_id, create_spec.to_hash).and_return(virtual_network)
    expect(EsxCloud::VirtualNetwork.create(project_id, create_spec)).to eq virtual_network
  end

  it "delegates find_all to client" do
    expect(client).to receive(:find_all_virtual_networks).and_return(virtual_network_list)
    expect(EsxCloud::VirtualNetwork.find_all).to eq virtual_network_list
  end

  it "delegates get to client" do
    expect(client).to receive(:find_virtual_network_by_id).with("id").and_return(virtual_network)
    expect(EsxCloud::VirtualNetwork.get("id")).to eq virtual_network
  end

  it "delegates deleted to client" do
    expect(client).to receive(:delete_virtual_network).with("id")
    virtual_network.delete
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "virtual_network_name",
        "description" => "virtual_network_desc",
        "state" => "virtual_network_state",
        "routingType" => "virtual_network_routing_type",
        "isDefault" => true,
        "cidr" => "virtual_network_cidr",
        "lowIpDynamic" => "virtual_network_low_ip_dynamic",
        "highIpDynamic" => "virtual_network_high_ip_dynamic",
        "lowIpStatic" => "virtual_network_low_ip_static",
        "highIpStatic" => "virtual_network_high_ip_static",
        "reservedIpList" => ["virtual_network_reserved_ip"]
    }
    from_hash = EsxCloud::VirtualNetwork.create_from_hash(hash)
    from_json = EsxCloud::VirtualNetwork.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |virtual_network|
      virtual_network.id.should == "foo"
      virtual_network.name.should == "virtual_network_name"
      virtual_network.description == "virtual_network_desc"
      virtual_network.state == "virtual_network_state"
      virtual_network.routing_type == "virtual_network_routing_type"
      virtual_network.is_default == true
      virtual_network.cidr == "virtual_network_cidr"
      virtual_network.low_ip_dynamic == "virtual_network_low_ip_dynamic"
      virtual_network.high_ip_dynamic == "virtual_network_high_ip_dynamic"
      virtual_network.low_ip_static == "virtual_network_low_ip_static"
      virtual_network.high_ip_static == "virtual_network_high_ip_static"
      virtual_network.reserved_ip_list == ["virtual_network_reserved_ip"]
    end
  end
end
