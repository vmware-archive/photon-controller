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

require 'fileutils'
require 'spec_helper'
require 'stats_helper'
require 'matchers/stats'
require 'pp'

describe "Agent stats plugin", stats: true do
  include StatsHelper

  before(:all) do
    @deployment = client.find_all_api_deployments.items.first
    @host = client.get_deployment_hosts(@deployment.id).items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
    @stats_endpoint = ENV["STATS_STORE_ENDPOINT"]
    @stats_port = ENV["STATS_STORE_PORT"]
    @graphite_web_port = ENV["GRAPHITE_WEB_PORT"] || 8000

    # Remove any previous test data. Do this here, rather
    # than in after(), so that data is visible after test runs.
    delete_graphite_data_devbox
  end

  after(:all) do
  end

  it "Publishes to Graphite successfully" do
    # Remove existing host
    expect(@host).not_to be_nil
    delete_host(@host.id)

    # Patch deployment and enable stats
    # set_stats_state(@deployment.id, true, @stats_endpoint, @stats_port)

    # Redeploy host
    @host = EsxCloud::Host.create(@deployment.id, @host.to_spec())

    stats = get_stats_from_graphite(@stats_endpoint, @graphite_web_port, "photon.*.CLOUD.cpu.cpuUsagePercentage")
    expect(stats).to have_graphite_data
  end
end
