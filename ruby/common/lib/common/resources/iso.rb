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
  class Iso

    attr_accessor :id, :name, :size

    # @param [String] json
    # @return [Iso]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name).to_set)
        fail UnexpectedFormat, "Invalid iso hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["size"])
    end

    # @param [String] id
    # @param [String] name
    # @param [Integer] size
    def initialize(id, name, size)
      @id = id
      @name = name
      @size = size
    end

    def to_hash
      {
          :id => @id,
          :name => @name,
          :size => @size
      }
    end

    def ==(other)
      @id == other.id && @name == other.name &&
        @size == other.size
    end

  end
end
