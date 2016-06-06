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

describe "virtual_network", :virtual_network => true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @project = @seeder.project!
  end

  let(:virtual_networks_to_delete) { [] }
  let(:virtual_network_name) { random_name("virtual-network-") }
  let(:spec) { EsxCloud::VirtualNetworkCreateSpec.new(virtual_network_name, "virtual network", "ROUTED") }

  after(:each) do
    virtual_networks_to_delete.each do |virtual_network|
      ignoring_all_errors { virtual_network.delete } unless virtual_network.nil?
    end
  end

  describe "#create" do
    it "should create one virtual network successfully" do
      network = create_virtual_network(@project.id, spec)
      expect(network.name).to eq virtual_network_name
      expect(network.description).to eq "virtual network"
      expect(network.state).to eq "READY"

      networks = client.find_virtual_networks_by_name(virtual_network_name).items
      expect(networks.size).to eq 1
      network_found = client.find_virtual_network_by_id(network.id)
      expect(network_found).to eq network
    end

    it "should create two virtual network successfully with the same names" do
      create_virtual_network(@project.id, spec)
      network = create_virtual_network(@project.id, spec)
      expect(network.name).to eq virtual_network_name

      networks = client.find_virtual_networks_by_name(spec.name).items
      expect(networks.size).to eq 2
    end

    it "should fail to create virtual network when name is not specified" do
      spec.name = nil
      begin
        create_virtual_network(@project.id, spec)
        fail("create virtual network should fail when name is not specified")
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

    it "should fail to create virtual network when name is invalid" do
      err_msg = "name : The specific virtual network name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was 1foo)"
      spec.name = "1foo"
      begin
        create_virtual_network(@project.id, spec)
        fail("create virtual network should fail when name is invalid")
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 400
        expect(e.errors.size).to eq 1
        expect(e.errors.first.message).to eq err_msg
      rescue EsxCloud::CliError => e
        expect(e.output).to include(err_msg)
      end
    end

    it "should fail to create virtual network when routing type is invalid" do
      error_msg = "The supplied JSON could not be parsed: ROUTED_ISOLATED_BOTH was not one of [ROUTED, ISOLATED]"

      spec.routing_type = "ROUTED_ISOLATED_BOTH"
      begin
        create_virtual_network(@project.id, spec)
        fail("create virtual network should fail when routing type is invalid")
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 400
        expect(e.errors.size).to eq 1
        expect(e.errors.first.message).to eq error_msg
      rescue EsxCloud::CliError => e
        if ENV["DRIVER"] == "gocli"
          expect(e.output).to include("routing type is invalid")
        else
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  private

  def create_virtual_network(project_id, spec)
    begin
      network = EsxCloud::VirtualNetwork.create(project_id, spec)
      virtual_networks_to_delete << network
      network
    rescue
      EsxCloud::VirtualNetwork.find_by_name(spec.name).items.each {|i| virtual_networks_to_delete << i}
      raise
    end
  end
end
