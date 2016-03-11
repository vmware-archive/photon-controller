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

describe "Agent stats plugin", stats: true, :order => :defined do
  include StatsHelper

  before(:all) do
    @stats_endpoint = ENV["STATS_STORE_ENDPOINT"]
    @stats_port = ENV["STATS_STORE_PORT"]
    @graphite_web_port = ENV["GRAPHITE_WEB_PORT"] || 8000
  end

  def ensure_host_exists
    @deployment = client.find_all_api_deployments.items.first
    @host = client.get_deployment_hosts(@deployment.id).items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
    if @host.nil?
      @host = EsxCloud::Host.create(@deployment.id, @host_spec)
    end
    @host_id = @host.id
    @host_spec = @host.to_spec
  end

  before(:each) do
    delete_graphite_data
    start_graphite
    ensure_host_exists
    delete_host(@host_id)
  end

  after(:each) do
    ensure_host_exists
  end

  def run_test
    @host = EsxCloud::Host.create(@deployment.id, @host_spec)
    @stats = get_stats_from_graphite(@stats_endpoint, @graphite_web_port, "photon.*.CLOUD.cpu.cpuUsagePercentage")
  end

  # Important: keep these in the defined order, with nil test cases first. Once the statsEndpoint/statsPort fields
  # are set in the deployment document, they can't be made nil again, so we need to keep the tests in order
  # and run those test cases first.
  describe "publish behavior when disabled", :order => :defined do
    context "when statsEnabled is false, and statsEndpoint and statsPort are nil" do
      before(:each) do
        delete_graphite_data
        set_stats_state(@deployment.id, false, nil, nil)
        run_test
      end

      it "does not publish to Graphite" do
        expect(@stats).not_to have_at_least(1).items
      end
    end

    context "when statsEnabled is true, statsEndpoint and statsPort are nil" do
      before(:each) do
        delete_graphite_data
        set_stats_state(@deployment.id, true, nil, nil)
        run_test
      end

      it "does not publish to Graphite" do
        expect(@stats).not_to have_at_least(1).items
      end
    end

    context "when statsEnabled is false, statsEndpoint is set, and statsPort is nil" do
      before(:each) do
        delete_graphite_data
        set_stats_state(@deployment.id, false, @stats_endpoint, nil)
        run_test
      end

      it "does not publish to Graphite" do
        expect(@stats).not_to have_at_least(1).items
      end
    end

    context "when statsEnabled is true, statsEndpoint is set, and statsPort is nil" do
      before(:each) do
        delete_graphite_data
        set_stats_state(@deployment.id, true, @stats_endpoint, nil)
        run_test
      end

      it "does not publish to Graphite" do
        expect(@stats).not_to have_at_least(1).items
      end
    end

    context "when statsEnabled is false, and statsEndpoint and statsPort are set" do
      before(:each) do
        delete_graphite_data
        set_stats_state(@deployment.id, false, @stats_endpoint, @stats_port)
        run_test
      end

      # expect(stats).to have_at_least(1).items
      it "does not publish to Graphite" do
        expect(@stats).not_to have_at_least(1).items
      end
    end

  end

  describe "successful Graphite publishing", :order => :defined do
    before(:each) do
      # Patch deployment and enable stats
      ensure_host_exists
      set_stats_state(@deployment.id, true, @stats_endpoint, @stats_port)

      # Redeploy host
      delete_host(@host_id)
      @host = EsxCloud::Host.create(@deployment.id, @host_spec)
      @host_id = @host.id
    end

    it "publishes to Graphite" do
      stats = get_stats_from_graphite(@stats_endpoint, @graphite_web_port, "photon.*.CLOUD.cpu.cpuUsagePercentage")
      expect(stats).to have_at_least(1).items
    end

    it "publishes after Graphite restarts" do
      stop_graphite
      delete_graphite_data
      # Simulate service being "unavailable"
      sleep(60)
      start_graphite

      # New stats should arrive after restart
      stats = get_stats_from_graphite(@stats_endpoint, @graphite_web_port, "photon.*.CLOUD.cpu.cpuUsagePercentage")
      expect(stats).to have_at_least(1).items
    end
  end
end
