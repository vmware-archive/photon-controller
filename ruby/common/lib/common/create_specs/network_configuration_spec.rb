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

module EsxCloud

  # Contains NetworkConfigurationSpec Info
  class NetworkConfigurationSpec

    attr_reader :virtual_network_enabled, :network_manager_address, :network_manager_username,
                :network_manager_password, :network_zone_id, :network_top_router_id

    # @param [String] json
    # @return [NetworkConfigurationSpec]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [NetworkConfigurationSpec]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(virtualNetworkEnabled).to_set)
        fail UnexpectedFormat, "Invalid NetworkConfiguration hash: #{hash}"
      end

      new(hash["virtualNetworkEnabled"], hash["networkManagerAddress"], hash["networkManagerUsername"],
          hash["networkManagerPassword"], hash["networkZoneId"], hash["networkTopRouterId"])
    end

    # @param [Boolean] virtual_network_enabled
    # @param [String] network_manager_address
    # @param [String] network_manager_username
    # @param [String] network_manager_password
    # @param [String] network_zone_id
    # @param [String] network_top_router_id
    def initialize(virtual_network_enabled, network_manager_address = nil, network_manager_username = nil,
                   network_manager_password = nil, network_zone_id = nil, network_top_router_id = nil)
      @virtual_network_enabled = virtual_network_enabled
      @network_manager_address = network_manager_address
      @network_manager_username = network_manager_username
      @network_manager_password = network_manager_password
      @network_zone_id = network_zone_id
      @network_top_router_id = network_top_router_id
    end

    # @param [NetworkConfigurationSpec] other
    def ==(other)
      @virtual_network_enabled == other.virtual_network_enabled &&
      @network_manager_address == other.network_manager_address &&
      @network_manager_username == other.network_manager_username &&
      @network_manager_password == other.network_manager_password &&
      @network_zone_id == other.network_zone_id &&
      @network_top_router_id == other.network_top_router_id
    end

    def to_hash
      {
          virtual_network_enabled: @virtual_network_enabled,
          network_manager_address: @network_manager_address,
          network_manager_username: @network_manager_username,
          network_manager_password: @network_manager_password,
          network_zone_id: @network_zone_id,
          network_top_router_id: @network_top_router_id
      }
    end

  end
end
