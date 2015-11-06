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

describe EsxCloud::VmNetworks do
  describe ".create_from_hash_array" do
    context "when there is no network" do
      let(:networks) {[]}
      it "returns empty nic list" do
        expect do
          described_class.create_from_hash_array(networks).network_connections.empty?
        end.to be_true
      end
    end

    context "when there is one nic" do
      let(:networks) do
        [{
          "network"=>"network",
          "macAddress"=>"mac",
          "ipAddress"=>"1.1.1.1",
          "netmask"=>"255.255.0.0",
          "isConnected"=>true
        }]
      end

      it "returns one nic information" do
        network = EsxCloud::NetworkConnection.new("network",
                                                  "mac",
                                                  "1.1.1.1",
                                                  "255.255.0.0",
                                                  true)
        described_class.create_from_hash_array(networks).network_connections.
          should eq([network])
      end
    end

    context "when there are two nics" do
      let(:networks) do
        [{
             "network"=>"network",
             "macAddress"=>"mac",
             "ipAddress"=>"1.1.1.1",
             "netmask"=>"255.255.0.0",
             "isConnected"=>true
         },
         {
             "network"=>"network2",
             "macAddress"=>"mac2",
             "ipAddress"=>"2.2.1.1",
             "netmask"=>"255.255.0.0",
             "isConnected"=>true
         }]
      end

      it "returns two nic informations" do
        network1 = EsxCloud::NetworkConnection.new("network",
                                                   "mac",
                                                   "1.1.1.1",
                                                   "255.255.0.0",
                                                   true)
        network2 = EsxCloud::NetworkConnection.new("network2",
                                                   "mac2",
                                                   "2.2.1.1",
                                                   "255.255.0.0",
                                                   true)
        described_class.create_from_hash_array(networks).network_connections.
            should eq([network1, network2])
      end
    end
  end

  describe ".create_from_task" do
    context "when there is no network" do
      let(:task) {task_done("task", "entity", "kind", [])}
      it "returns empty nic list" do
        expect do
          described_class.create_from_task(task).network_connections.empty?
        end.to be_true
      end
    end
  end

  context "when there is one nic" do
    let(:networks) do
      [{
           "network"=>"network",
           "macAddress"=>"mac",
           "ipAddress"=>"1.1.1.1",
           "netmask"=>"255.255.0.0",
           "isConnected"=>true
       }]
    end
    let(:task) {make_task(network)}

    it "returns one nic information" do
      network = EsxCloud::NetworkConnection.new("network",
                                                "mac",
                                                "1.1.1.1",
                                                "255.255.0.0",
                                                true)
      described_class.create_from_hash_array(networks).network_connections.
          should eq([network])
    end
  end

  context "when there are two nics" do
    let(:networks) do
      [{
           "network"=>"network",
           "macAddress"=>"mac",
           "ipAddress"=>"1.1.1.1",
           "netmask"=>"255.255.0.0",
           "isConnected"=>true
       },
       {
           "network"=>"network2",
           "macAddress"=>"mac2",
           "ipAddress"=>"2.2.1.1",
           "netmask"=>"255.255.0.0",
           "isConnected"=>true
       }]
    end
    let(:task) {make_task(network)}

    it "returns two nic informations" do
      network1 = EsxCloud::NetworkConnection.new("network",
                                                 "mac",
                                                 "1.1.1.1",
                                                 "255.255.0.0",
                                                 true)
      network2 = EsxCloud::NetworkConnection.new("network2",
                                                 "mac2",
                                                 "2.2.1.1",
                                                 "255.255.0.0",
                                                 true)
      described_class.create_from_hash_array(networks).network_connections.
          should eq([network1, network2])
    end
  end

  context "when ip_address in nil" do
    let(:networks) do
      [{
           "network"=>"network",
           "macAddress"=>"mac",
           "ipAddress"=>nil,
           "netmask"=>nil,
           "isConnected"=>true
       }]
    end
    let(:task) {make_task(network)}

    it "returns one nic information" do
      network = EsxCloud::NetworkConnection.new("network",
                                                "mac",
                                                nil,
                                                nil,
                                                true)
      described_class.create_from_hash_array(networks).network_connections.
          should eq([network])
    end
  end

  private

  def make_task(networks)
    task_done("task", "entity", "kind", networks)
  end
end
