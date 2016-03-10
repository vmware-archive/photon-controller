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

require 'net/http'
require 'json'
require 'fileutils'
require 'dcp/cloud_store/cloud_store_client'

# Helper module for stats integration tests
module StatsHelper
  module_function

  # Gets stats from a Graphite endpoint. Polls endpoint until data comes back,
  # or returns an empty result if no data available.
  def get_stats_from_graphite(endpoint, port, pattern)
    uri = URI.parse("http://#{endpoint}:#{port}/render?target=#{pattern}&format=json")
    tries = 0
    begin
      res = Net::HTTP.get(uri)
      json = JSON.parse(res)
      tries += 1
      sleep(5)
    end until json.length > 0 or tries > 20
    return json
  end

  def delete_graphite_data_devbox
    top_dir = %x(git rev-parse --show-toplevel)
    whisper_dir = File.join(top_dir, "devbox-photon", "stats_data", "graphite", "graphite", "whisper", "photon")
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
