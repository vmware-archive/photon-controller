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
require_relative "../../../lib/dcp/cloud_store/host_factory"

describe "Host monitor", monitor_host: true do

  it "verifies that the newly created host is pingable" do
    EsxCloud::Config.init
    EsxCloud::Config.client = ApiClientHelper.management

    deployment = client.find_all_api_deployments.items[0]
    host = client.get_deployment_hosts(deployment.id).items.first
    host_service = EsxCloud::Dcp::CloudStore::HostFactory.get_host host.id

    # Poll the host service for 120 seconds before giving up
    # This is needed since we have a maintenance interval of 60 and a randomness of 50 seconds
    max_poll_timeout = 120
    poll_interval = 5
    sleep_counter = 0
    
    while host_service["agentState"] != "ACTIVE" and sleep_counter <= max_poll_timeout
      sleep poll_interval
      sleep_counter += poll_interval
      host_service = EsxCloud::Dcp::CloudStore::HostFactory.get_host host.id
    end

    expect(host_service["agentState"]).to eq("ACTIVE")
  end
end
