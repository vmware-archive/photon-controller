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

  # Contains Stats Info
  class StatsInfo

    attr_reader :enabled, :storeEndpoint, :storePort, :storeType

    # @return [StatsInfo]
    def self.get
      Config.client.get_stats_info
    end

    # @param [String] json
    # @return [StatsInfo]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [StatsInfo]
    def self.create_from_hash(hash)


      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(enabled).to_set) && !hash.nil?
        fail UnexpectedFormat, "Invalid StatsInfo hash: #{hash}"
      end

      new(hash["enabled"], hash["storeEndpoint"], hash["storePort"], hash["storeType"]) unless hash.nil?
    end

    # @param [Boolean] enabled
    # @param [String] storeEndpoint
    # @param [String] storePort
    # @param [String] storeType
    def initialize(enabled, storeEndpoint = nil, storePort = nil, storeType = nil)
      @enabled = enabled
      @storeEndpoint = storeEndpoint
      @storePort = storePort
      @storeType = storeType
    end

    # @param [StatsInfo] other
    def ==(other)
      @enabled == other.enabled &&
        @storeEndpoint == other.storeEndpoint &&
        @storePort == other.storePort &&
        @storeType == other.storeType
    end

    def to_hash
      {
        enabled: @enabled,
        storeEndpoint: @storeEndpoint,
        storePort: @storePort,
        storeType: @storeType
      }
    end

  end
end
