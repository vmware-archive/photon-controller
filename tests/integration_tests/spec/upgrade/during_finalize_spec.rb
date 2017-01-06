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

describe "migrate finalize" do
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

  it "should pause both systems during finalize" do
    client.resume_system(destination_deployment.id)
    source_api_client.resume_system(source_deployment.id)
    client.finalize_deployment_migration(EsxCloud::TestHelpers.get_upgrade_source_address,
                                         destination_deployment.id)

    # the destination system should be resumed
    create_tenant(name: random_name("tenant-"))

    begin
      # the source system should be frozen
      source_api_client.create_tenant(name: random_name("tenant-"))
      fail("source system should be paused")
    rescue EsxCloud::ApiError => e
      expect(e.response_code).to eq 403
      expect(e.errors.size).to eq 1
      expect(e.errors[0].code).to eq "SystemPaused"
      expect(e.errors[0].message).to match /System is paused/
    end
  end

end