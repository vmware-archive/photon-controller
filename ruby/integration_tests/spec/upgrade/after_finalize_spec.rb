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

require 'spec_helper'
require 'thrift/thrift_helper'

require 'uri'
require 'net/http'

require 'agent_control'

require 'dcp/cloud_store/cloud_store_client'

describe "migrate finalize" do #, upgrade: true do

  DOCKER_PORT = 2375

  before (:all) {
    puts "Source Address:"
    puts EsxCloud::TestHelpers.get_upgrade_source_address
  }

  let(:source_api_client) {
    puts "Source Address:"
    puts EsxCloud::TestHelpers.get_upgrade_source_address
    uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
    ApiClientHelper.management(protocol: uri.scheme, address: uri.host, port: uri.port.to_s)
  }

  let(:destination_deployment) do
    client.find_all_api_deployments.items.first
  end

  let(:source_deployment) do
    source_api_client.find_all_api_deployments.items.first
  end

  describe "#data_check" do
    it "should destination contain all the cloudstore content of the source" do
      upgrade_cloudstore_map = {
          "/esxcloud/cloudstore/datastores" => "/photon/cloudstore/datastores",
          "/esxcloud/cloudstore/hosts" => "/photon/cloudstore/hosts",
          "/esxcloud/cloudstore/entity-locks" => "/photon/cloudstore/entity-locks",
          "/esxcloud/cloudstore/attached-disks" => "/photon/cloudstore/attached-disks",
          "/esxcloud/cloudstore/tombstones" => "/photon/cloudstore/tombstones",
          "/esxcloud/cloudstore/flavors" => "/photon/cloudstore/flavors",
          "/esxcloud/cloudstore/images" => "/photon/cloudstore/images",
          "/esxcloud/cloudstore/networks" => "/photon/cloudstore/networks",
          "/provisioning/esxcloud/portgroups" => "/photon/cloudstore/portgroups",
          "/esxcloud/cloudstore/projects" => "/photon/cloudstore/projects",
          "/esxcloud/cloudstore/tenants" => "/photon/cloudstore/tenants",
          "/esxcloud/cloudstore/resource-tickets" => "/photon/cloudstore/resource-tickets",
          "/esxcloud/cloudstore/vms" => "/photon/cloudstore/vms",
          "/esxcloud/cloudstore/disks" => "/photon/cloudstore/disks",
          "/esxcloud/cloudstore/clusters" => "/photon/cloudstore/clusters",
          "/photon/cloudstore/datastores" => "/photon/cloudstore/datastores",
          "/photon/cloudstore/hosts" => "/photon/cloudstore/hosts",
          "/photon/cloudstore/entity-locks" => "/photon/cloudstore/entity-locks",
          "/photon/cloudstore/attached-disks" => "/photon/cloudstore/attached-disks",
          "/photon/cloudstore/tombstones" => "/photon/cloudstore/tombstones",
          "/photon/cloudstore/flavors" => "/photon/cloudstore/flavors",
          "/photon/cloudstore/images" => "/photon/cloudstore/images",
          "/photon/cloudstore/networks" => "/photon/cloudstore/networks",
          "/photon/cloudstore/portgroups" => "/photon/cloudstore/portgroups",
          "/photon/cloudstore/projects" => "/photon/cloudstore/projects",
          "/photon/cloudstore/tenants" => "/photon/cloudstore/tenants",
          "/photon/cloudstore/resource-tickets" => "/photon/cloudstore/resource-tickets",
          "/photon/cloudstore/vms" => "/photon/cloudstore/vms",
          "/photon/cloudstore/disks" => "/photon/cloudstore/disks",
          "/photon/cloudstore/clusters" => "/photon/cloudstore/clusters"}
      uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
      source_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil)

      destination_uri = URI.parse(ApiClientHelper.endpoint(nil, nil, nil))
      destination_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(
          destination_uri.host, nil)

      upgrade_cloudstore_map.each do |k, v|
        puts k
        begin
          source_json = source_cloud_store.get k
        rescue StandardError => e
          next if e.message.include? "404"
          raise e
        end
        source_set = parse_id_set(source_json)
        destination_json = destination_cloud_store.get v
        destination_set = parse_id_set(destination_json)
        expect(destination_set.superset?(source_set)).to eq true
      end
    end
  end

  describe "#old plane state" do
    it "should not be running housekeeper" do
      source_api_client.get_deployment_hosts(source_deployment.id).items.each do |host|
        if host.usage_tags.include? "MGMT"
          vm_ip = host.metadata["MANAGEMENT_NETWORK_IP"]
          uri = URI("http://#{vm_ip}:#{DOCKER_PORT}/containers/json")
          uri.query = URI.encode_www_form({ :all => false })
          res = Net::HTTP.get(uri)
          fail("HouseKeeper container on #{vm_ip} still running") unless JSON.parse(res)[0]["Names"].select { |name| name.downcase.include? "housekeeper" }.empty?
        end
      end
    end
  end

  describe "agent roll out" do
    it "should have rolled out the new agent to all hosts" do
      client.get_deployment_hosts(destination_deployment.id).items.each do |host|
        protocol = Photon::ThriftHelper.get_protocol(host.address, 8835, "AgentControl")
        agent_client = AgentControl::Client.new(protocol)

        req = VersionRequest.new
        res = agent_client.get_version req
        expect(res.version).to eq "0.1.2"
      end
    end
  end
end
