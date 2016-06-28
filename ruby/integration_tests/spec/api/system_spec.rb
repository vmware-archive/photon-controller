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
require "date"
require_relative "../../lib/host_cleaner"

describe "system" do
  let(:api_client) { ApiClientHelper.management }

  # The /available API returns an empty response. It's used by a
  # front-end load balancer to detect if the system is up and running
  it "is available", management: true do
    available = api_client.get_available
    expect(available).to_not be_nil
  end

  it "shows system status as ready", management: true do
    system_status = api_client.get_status
    expect(system_status.status).to_not be_nil
    expect(system_status.components.size).to eq 1

    system_status.components.each do |component|
      expect(component.name).to_not be_nil
      expect(component.status).to_not be_nil
    end
  end

  it "validates tenants, images and hosts are clean", validate_system: true, devbox: true do
    system_cleaner = EsxCloud::SystemCleaner.new(api_client)
    stat = system_cleaner.clean_system

    default_network_ids = [EsxCloud::SystemSeeder.instance.network.id]
    network_ids = stat.delete "network"
    xor_network_ids = (network_ids | default_network_ids) - (network_ids & default_network_ids)
    expect(xor_network_ids.length).to eq(0)
    expect(stat).to be_empty, "Expect no garbage to be cleaned but found some: #{stat.inspect}"

    next unless ENV["DEVBOX"]
    dirty_vms = EsxCloud::HostCleaner.clean_vms_on_real_host(get_esx_ip, get_esx_username, get_esx_password)
    expect(dirty_vms).to be_nil, "VMs left on host #{get_esx_ip}: #{dirty_vms}"
  end

  it "validates deployment object", validate_system: true, devbox: true do
    deployments = api_client.find_all_api_deployments
    expect(deployments.items.size).to eq 1
    expect(deployments.items[0].state).to eq "READY"
  end
end
