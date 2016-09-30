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

require "spec_helper"

describe "virtual_network_lifecyle", :virtual_network => true do
  let(:networks_to_delete) { [] }
  let(:spec) {
    EsxCloud::VirtualNetworkCreateSpec.new(random_name("network-"), "virtual network", "ROUTED", 128, 16)
  }

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @project = @seeder.project!
  end

  after(:each) do
    networks_to_delete.each do |network|
      ignoring_all_errors { network.delete } unless network.nil?
    end
  end

  it "Creates a virtual network, and then delete it" do
    network = create_network(spec)

    retrieved_network = EsxCloud::VirtualNetwork.get(network.id)
    expect(retrieved_network.id).to eq(network.id)

    retrieved_network_list = client.get_project_networks(@project.id, spec.name)
    expect(retrieved_network_list.items.count).to eq(1)
    expect(retrieved_network_list.items.first.id).to eq(network.id)

    retrieved_network_list = client.get_project_networks(@project.id)
    expect(retrieved_network_list.items.count).to be >= 1

    network.delete
  end

  xit "Creates two virtual networks, two VMs and then delete them" do
    network1 = create_network(spec)
    network2 = create_network(spec)

    retrieved_network_list = client.get_project_networks(@project.id)
    expect(retrieved_network_list.items.count).to eq(2)
    retrieved_network1 = retrieved_network_list.items[0]
    retrieved_network2 = retrieved_network_list.items[1]

    vm1 = create_vm(@project, { networks: [retrieved_network1.id] })
    vm1.start!

    vm2 = create_vm(@project, { networks: [retrieved_network2.id] })
    vm2.start!

    network1_connections = client.get_vm_networks(vm1.id).network_connections
    network_match = false
    network1_connections.each do |nc|
      if nc.network == retrieved_network1.id
        network_match = true
        break
      end
    end
    expect(network_match).to be_true

    network1_connection_ips = network1_connections.map { |nc| nc.ip_address }
    retrieved_network1_range = retrieved_network1.low_ip_dynamic + "-" + retrieved_network1.high_ip_dynamic
    expect((network1_connection_ips-IPRange.parse(retrieved_network1_range)).empty?).to be_true

    network2_connections = client.get_vm_networks(vm2.id).network_connections
    network_match = false
    network2_connections.each do |nc|
      if nc.network == retrieved_network2.id
        network_match = true
        break
      end
    end
    expect(network_match).to be_true

    network2_connection_ips = network2_connections.map { |nc| nc.ip_address }
    retrieved_network2_range = retrieved_network2.low_ip_dynamic + "-" + retrieved_network2.high_ip_dynamic
    expect((network2_connection_ips-IPRange.parse(retrieved_network2_range)).empty?).to be_true

    vm1.stop!
    vm1.delete

    vm2.stop!
    vm2.delete
  end

  private

  def create_network(spec)
    begin
      EsxCloud::VirtualNetwork.create(@project.id, spec)
    rescue
      client.get_project_networks(@project.id, spec.name).items.each { |net| networks_to_delete << net }
      raise
    end
  end
end
