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
  class PortGroup

    attr_accessor :id, :name, :usage_tags

    # @param [String] json
    # @return [PortGroup]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Network]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name).to_set)
        fail UnexpectedFormat, "Invalid PortGroup hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["usageTags"])
    end

    # @param [String] id
    # @param [String] name
    # @param [Array<String>] usage_tags
    def initialize(id, name, usage_tags)
      @id = id
      @name = name
      @usage_tags = usage_tags
    end

    def ==(other)
      @id == other.id &&
        @name == other.name &&
        @usage_tags == other.usage_tags
    end

  end
end