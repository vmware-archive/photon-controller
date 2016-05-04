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

require_relative "../../lib/dcp/cloud_store/cloud_store_client"

class HousekeeperHelper

  def self.clean_unreachable_datastores
    client = EsxCloud::Dcp::CloudStore::CloudStoreClient.instance
    hosts = client.get "/photon/cloudstore/hosts?expand"
    hosts["documents"].each do |document|
      host = document[1]
      if host["state"] == "ERROR"
        begin
          local_datastore = host["datastoreServiceLinks"]["datastore1"]
          client.delete local_datastore unless local_datastore.nil?
        rescue Exception => e
          STDERR.puts "  ignoring error: #{e}"
        end
      end
    end
  end

end
