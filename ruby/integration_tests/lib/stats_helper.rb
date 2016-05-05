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

require 'date'
require 'net/http'
require 'json'
require 'fileutils'
require 'dcp/cloud_store/cloud_store_client'
require 'pp'

# Helper module for stats integration tests
module StatsHelper
  @@git_dir = %x(git rev-parse --show-toplevel).strip

  module_function

  # Gets stats from a Graphite endpoint. Polls endpoint until data comes back,
  # or returns an empty result if no data available.
  def get_stats_from_graphite(endpoint, port, pattern)
    uri = URI.parse("http://#{endpoint}:#{port}/render?target=#{pattern}&format=json")
    max_seconds = 60
    start = Time.now
    # Wait for stats server to start sending data
    sleep(5)
    begin
      res = Net::HTTP.get(uri)
      json = JSON.parse(res)
      sleep(5)
    end until json.length > 0 || (Time.now - start) > max_seconds

    if json.nil? || json.first.nil? || json.first["datapoints"].nil?
      puts "Graphite server not responding with data"
      return
    end

    len = json.first["datapoints"].length
    start_time = json.first["datapoints"][0].last
    end_time = json.first["datapoints"][len-1].last
    start_time_utc = Time.at(start_time).utc.to_datetime
    end_time_utc = Time.at(end_time).utc.to_datetime
    pp "Graphite server collection Start time: #{start_time_utc}, End time: #{end_time_utc}"
    json.first["datapoints"].select { |x| x.first != nil }
  end


  def delete_graphite_data_devbox
    whisper_dir = File.join(@@git_dir, "devbox-photon", "stats_data", "graphite", "graphite", "whisper", "photon")
    FileUtils.rm_rf(whisper_dir)
  end

  # Patch deployment to set stats state
  def set_stats_state(deployment_id, stats_enabled, stats_endpoint, stats_port)
    patch = {
      statsEnabled: stats_enabled,
      statsStoreEndpoint: stats_endpoint,
      statsStorePort: stats_port
    }
    link = "/photon/cloudstore/deployments/#{@deployment.id}"
    EsxCloud::Dcp::CloudStore::CloudStoreClient.instance.patch(link, patch)
  end
end
