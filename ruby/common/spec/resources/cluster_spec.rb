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

describe EsxCloud::Cluster do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  describe "#create" do
    it "delegates create to API client" do
      spec = EsxCloud::ClusterCreateSpec.new(
            "name",
            "KUBERNETES",
            "core-100",
            "core-100",
            "network",
            2,
            5,
            {
              "dns" => "10.0.0.1",
              "gateway" => "10.0.0.2",
              "netmask" => "255.255.255.128",
              "master_ip" => "10.0.0.3",
              "container_network" => "10.0.0.0/24"
            }
      )

      spec_hash = {
        :name => "name",
        :type => "KUBERNETES",
        :vmFlavor => "core-100",
        :diskFlavor => "core-100",
        :vmNetworkId => "network",
        :workerCount => 2,
        :workerBatchExpansionSize => 5,
        :extendedProperties => {
          "dns" => "10.0.0.1",
          "gateway" => "10.0.0.2",
          "netmask" => "255.255.255.128",
          "master_ip" => "10.0.0.3",
          "container_network" => "10.0.0.0/24"
        }
      }

      expect(@client).to receive(:create_cluster).with("project_id", spec_hash)

      EsxCloud::Cluster.create("project_id", spec)
    end
  end

  describe "#create_from_hash, #create_from_json" do
    context "when both json and hash contain all required keys" do
      it "can be created from hash or from JSON" do
        hash = {
          "id" => "id",
          "name" => "name",
          "type" => "KUBERNETES",
          "state" => "CREATING",
          "workerCount" => 2,
          "extendedProperties" => {
            "dns" => "10.0.0.1",
            "gateway" => "10.0.0.2",
            "netmask" => "255.255.255.128",
            "master_ip" => "10.0.0.3",
            "container_network" => "10.0.0.0/24"
          }
        }
        from_hash = EsxCloud::Cluster.create_from_hash(hash)
        from_json = EsxCloud::Cluster.create_from_json(JSON.generate(hash))

        [from_hash, from_json].each do |cluster|
          cluster.id.should == "id"
          cluster.name.should == "name"
          cluster.type.should == "KUBERNETES"
          cluster.state.should == "CREATING"
          cluster.worker_count.should == 2
          cluster.extended_properties["dns"].should == "10.0.0.1"
          cluster.extended_properties["gateway"].should == "10.0.0.2"
          cluster.extended_properties["netmask"].should == "255.255.255.128"
          cluster.extended_properties["master_ip"].should == "10.0.0.3"
          cluster.extended_properties["container_network"].should == "10.0.0.0/24"
        end
      end
    end

    context "when hash contains no key" do
      it "expects hash to have all required keys" do
        expect { EsxCloud::Cluster.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
      end
    end
  end

end
