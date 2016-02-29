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

describe "network", management: true do

  let(:network_name) { random_name("network-") }
  let(:portgroup) { random_name("port-group-") }
  let(:spec) { EsxCloud::NetworkCreateSpec.new(network_name, "VLAN", [portgroup]) }

  describe "#create" do

    it "should create one network successfully" do
      network = client.create_network(spec.to_hash)
      network_id = network.id
      expect(network.name).to eq network_name
      expect(network.description).to eq "VLAN"
      expect(network.state).to eq "READY"
      expect(network.portgroups).to eq [portgroup]

      networks = client.find_networks_by_name(network_name).items
      expect(networks.size).to eq 1
      network_found = client.find_network_by_id(network_id)
      expect(network_found).to eq network
    end

    context "when name is not specified" do
      it "should fail to create network" do
        spec.name = nil
        begin
          client.create_network(spec.to_hash)
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
          client.create_network(spec.to_hash)
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
          client.create_network(spec.to_hash)
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

    context "network with the same name already exists", disable_for_cli_test: true do
      before(:each) do
        client.create_network(spec.to_hash)
      end

      it "should create network successfully" do
        spec.portgroups = [random_name("port-group-")]
        network = client.create_network(spec.to_hash)
        expect(network.name).to eq network_name

        networks = client.find_all_networks.items.select { |i| i.name == network_name }
        expect(networks.size).to eq 2
      end
    end

  end

  describe "#delete" do

    context "when network is in READY", dcp: true do

      let(:network_id) do
        client.create_network(spec.to_hash)
        networks = client.find_all_networks.items.select { |i| i.name == network_name }
        expect(networks.size).to eq 1
        expect(networks.first.state).to eq "READY"
        networks.first.id
      end

      it "should delete network successfully" do
        expect(client.delete_network(network_id)).to be_true
      end
    end

    context "when network is in PENDING_DELETE", dcp: true do

      let(:network_id) do
        client.create_network(spec.to_hash)
        networks = client.find_all_networks.items.select { |i| i.name == network_name }
        expect(networks.size).to eq 1
        expect(networks.first.state).to eq "READY"
        network_id = networks.first.id

        expect(client.delete_network(network_id)).to be_true
        networks = client.find_all_networks.items.select { |i| i.name == network_name }
        expect(networks.size).to eq 1
        expect(networks.first.state).to eq "PENDING_DELETE"
        expect(networks.first.id).to eq network_id
        network_id
      end

      it "should fail to delete PENDING_DELETE network" do
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

  describe "#set_portgroups", dcp: true do

    it "sets portgroups successfully" do
      network = client.create_network(spec.to_hash)
      network_id = network.id
      expect(network.portgroups).to eq ["P1", "P2"]

      network = client.set_portgroups(network_id, ["P3"])
      expect(network.portgroups).to eq ["P3"]
    end

    it "should fail when network does not exit" do
      error_msg = "Network non-existing-network not found"
      begin
        client.set_portgroups("non-existing-network", ["P3"])
        fail("set_portgroups should fail when the network does not exist")
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

end
