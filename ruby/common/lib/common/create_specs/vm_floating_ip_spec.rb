# Copyright 2016 VMware, Inc. All Rights Reserved.
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

  # Contains VmFloatingIpSpec Info
  class VmFloatingIpSpec

    attr_reader :network_id

    # @param [String] json
    # @return [VmFloatingIpSpec]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [VmFloatingIpSpec]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(networkId).to_set)
        fail UnexpectedFormat, "Invalid VmFloatingIpSpec hash: #{hash}"
      end

      new(hash["networkId"])
    end

    # @param [String] network_id
    def initialize(network_id)
      @network_id = network_id
    end

    # @param [VmFloatingIpSpec] other
    def ==(other)
      @network_id == other.network_id
    end

    def to_hash
      {
          networkId: @network_id
      }
    end

  end
end
