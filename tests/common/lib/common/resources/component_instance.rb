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
  class ComponentInstance

    attr_accessor :address, :status, :message, :stats

    # @param [String] address
    # @param [String] status
    # @param [String] message
    # @param [Hash] stats
    def initialize(address, status, message, stats)
      @address = address
      @status = status
      @message = message
      @stats = stats
    end

    def ==(other)
      @address == other.address &&
        @status == other.status &&
        @message == other.message &&
        @stats == other.stats
    end

    # @param [String] json
    # @return [ComponentInstance]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [ComponentInstance]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(address status).to_set)
        fail UnexpectedFormat, "Invalid component instance hash: #{hash}"
      end

      new(hash["address"], hash["status"], hash["message"], hash["stats"])
    end

    def to_s
      "#{address}: #{status}, message: #{message}, stats: #{stats}"
    end

  end
end