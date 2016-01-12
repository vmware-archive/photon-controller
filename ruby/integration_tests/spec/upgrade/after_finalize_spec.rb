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
require 'uri'
require_relative "../../lib/dcp/cloud_store/cloud_store_client"

describe "migrate finalize", upgrade: true do
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
          "/esxcloud/cloudstore/clusters" => "/photon/cloudstore/clusters"}
      uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
      source_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil)

      destination_uri = URI.parse(ApiClientHelper.endpoint(nil, nil, nil))
      destination_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(
          destination_uri.host, nil)

      upgrade_cloudstore_map.each do |k, v|
        puts k
        source_json = source_cloud_store.get k
        source_set = parse_id_set(source_json)
        destination_json = destination_cloud_store.get v
        destination_set = parse_id_set(destination_json)
        expect(destination_set.superset?(source_set)).to eq true
      end
    end
  end
end
