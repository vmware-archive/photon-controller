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

require_relative "cloud_store_client"

module EsxCloud
  module Dcp
    module CloudStore
      class HostFactory
        FACTORY_SERVICE_LINK = "/photon/cloudstore/hosts"

        def initialize()
        end

        def self.get_host(id)
          CloudStoreClient.instance.get "#{FACTORY_SERVICE_LINK}/#{id}"
        end

        def self.get_all_hosts
          CloudStoreClient.instance.get "#{FACTORY_SERVICE_LINK}"
        end
      end
    end
  end
end
