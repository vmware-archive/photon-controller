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

require_relative "client"

module EsxCloud
  module Dcp
    class HouskeeperClient
      def self.instance
        @instance ||= Dcp::Client.new endpoint
      end

      def self.endpoint
        "http://#{address}:#{port}"
      end

      def self.address
        ENV["HOUSEKEEPER_ADDRESS"] ||
            ENV["API_ADDRESS"] ||
            ENV["PUBLIC_NETWORK_IP"] ||
            ENV["PRIVATE_NETWORK_IP"] ||
            raise("Could not determine Housekeeper IP." +
                  "Please set one of HOUSEKEEPER_ADDRESS, API_ADDRESS, PUBLIC_NETWORK_IP or PRIVATE_NETWORK_IP.")
      end

      def self.port
        ENV["HOUSEKEEPER_DCP_PORT"] || "16001"
      end
    end
  end
end

