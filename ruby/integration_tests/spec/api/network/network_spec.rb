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

describe "network", management: true, disable_for_cli_test: true do
  let(:networks_to_delete) { [] }

  let(:network_name) { random_name("network-") }
  let(:portgroup) { get_vm_port_group2 }
  let(:spec) { EsxCloud::NetworkCreateSpec.new(network_name, "VLAN", [portgroup]) }

  after(:each) do
    networks_to_delete.each do |network|
      ignoring_all_errors { network.delete } unless network.nil?
    end
  end

  describe "#create" do
    it "should create one network successfully" do
      network = create_network(spec)
      expect(network.name).to eq network_name
      expect(network.description).to eq "VLAN"
      expect(network.state).to eq "READY"
      expect(network.portgroups).to eq [portgroup]

      networks = client.find_networks_by_name(network_name).items
      expect(networks.size).to eq 1
      network_found = client.find_network_by_id(network.id)
      expect(network_found).to eq network
    end

    context "when name is not specified" do
      it "should fail to create network" do
        spec.name = nil
        begin
          create_network(spec)
          fail("create network should fail when name is not specified")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq "InvalidEntity"
        rescue EsxCloud::CliError => e
          if ENV["DRIVER"] == "gocli"
            expect(e.output).to include("Please provide network name")
          else
            expect(e.output).to include("InvalidEntity")
          end
        end
      end
    end

    context "when name is invalid" do
      it "should fail to create network" do
        error_msg = "name : The specified network name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was 1foo)"

        spec.name = "1foo"
        begin
          create_network(spec)
          fail("create network should fail when name is invalid")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.message).to eq error_msg
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end

    context "when port groups are empty" do
      it "should fail to create network" do
        error_msg = "portGroups size must be between 1 and 2147483647 (was [])"

        spec.portgroups = []
        begin
          create_network(spec)
          fail("create network should fail when port groups are empty")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.message).to eq error_msg
        rescue EsxCloud::CliError => e
          if ENV["DRIVER"] == "gocli"
            expect(e.output).to include("Please provide portgroups")
          else
            expect(e.output).to include(error_msg)
          end
        end
      end
    end

    context "network with the same name already exists" do
      before(:each) do
        create_network(spec)
      end

      it "should create network successfully" do
        spec.portgroups = [get_mgmt_port_group]
        network = create_network(spec)
        expect(network.name).to eq network_name

        networks = client.find_all_networks.items.select { |i| i.name == network_name }
        expect(networks.size).to eq 2
      end
    end
  end

  describe "#delete" do
    context "when network is in READY" do
      let(:network_id) do
        network = EsxCloud::Network.create(spec)
        expect(network.state).to eq "READY"
        network.id
      end

      it "should delete network successfully" do
        expect(client.delete_network(network_id)).to be_true
      end
    end

    context "when network is in PENDING_DELETE" do
      let(:network_id) do
        network = EsxCloud::Network.create(spec)
        expect(network.state).to eq "READY"
        network_id = network.id

        expect(client.delete_network(network_id)).to be_true
        networks = client.find_all_networks.items.select { |i| i.name == network_name }
        expect(networks.size).to eq 1
        expect(networks.first.state).to eq "PENDING_DELETE"
        expect(networks.first.id).to eq network_id
        network_id
      end

      xit "should fail to delete PENDING_DELETE network" do
        begin
          client.delete_network(network_id)
          fail "delete network in PENDING_DELETE state should fail"
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq "InvalidNetworkState"
        rescue EsxCloud::CliError => e
          expect(e.message).to match("1")
        end
      end
    end
  end

  describe "#set_portgroups" do
    let(:network) { create_network(spec) }
    let(:new_port_groups) { [get_mgmt_port_group] }

    it "sets portgroups successfully" do
      expect(network.portgroups).to eq [portgroup]

      client.set_portgroups(network.id, new_port_groups)
      network_found = client.find_network_by_id(network.id)
      expect(network_found.portgroups).to eq new_port_groups
    end

    context "when network does not exist" do
      it "fails to update portgroups" do
        error_msg = "Network non-existing-network not found"
        begin
          client.set_portgroups("non-existing-network", new_port_groups)
          fail("set_portgroups should fail")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("NetworkNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end

    context "when portgroup does not exist" do
      let(:new_port_groups) { ["missing-portgroup"] }

      it "fails to update portgroups" do
        error_msg = "Port group missing-portgroup does not exist on any host"
        begin
          client.set_portgroups(network.id, new_port_groups)
          fail("set_portgroups should fail")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("PortGroupsDoNotExist")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end

    context "when portgroup is already in use" do
      let(:new_port_groups) { [get_vm_port_group] }

      it "fails to update portgroups" do
        error_msg = "Port group #{get_vm_port_group} is already added to network"
        begin
          client.set_portgroups(network.id, new_port_groups)
          fail("set_portgroups should fail")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("PortGroupAlreadyAddedToNetwork")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#set_default" do
    before(:each) do
      @default_network = client.find_all_networks.items.find { |n| n.is_default }
    end

    after(:each) do
      client.set_default(@default_network.id) unless @default_network.nil?
    end

    xit "sets default network successfully without existing default network" do
      expect(@default_network).to be_nil

      network = create_network(spec)
      expect(network.is_default).to be_false

      expect(client.set_default(network.id)).to be_true
      network = client.find_network_by_id(network.id)
      expect(network.is_default).to be_true
    end

    it "sets default network successfully with existing default network" do
      network1 = client.find_all_networks.items.find { |n| n.is_default }
      expect(network1).to_not be_nil

      network2 = create_network(spec)
      expect(client.set_default(network2.id)).to be_true

      network1 = client.find_network_by_id(network1.id)
      network2 = client.find_network_by_id(network2.id)
      expect(network1.is_default).to be_false
      expect(network2.is_default).to be_true
    end

    it "sets same default network multiple times" do
      network = create_network(spec)
      expect(network.is_default).to be_false

      expect(client.set_default(network.id)).to be_true
      network = client.find_network_by_id(network.id)
      expect(network.is_default).to be_true

      expect(client.set_default(network.id)).to be_true
      network = client.find_network_by_id(network.id)
      expect(network.is_default).to be_true
    end
  end

  private

  def create_network(spec)
    begin
      network = EsxCloud::Network.create(spec)
      networks_to_delete << network
      network
    rescue
      EsxCloud::Network.find_by_name(spec.name).items.each {|i| networks_to_delete << i}
      raise
    end
  end
end
