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
  class Datastore

    attr_reader :id, :kind, :type, :tags

    # @param [String] json
    # @return [Datastore]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Disk]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id kind type tags).to_set)
        raise UnexpectedFormat, "Invalid Datastore hash: #{hash}"
      end

      new(hash["id"], hash["kind"], hash["type"], hash["tags"])
    end

    # @param [String] id
    # @param [String] kind
    # @param [String] type
    # @param [Array<String>] tags
    def initialize(id,kind, type, tags)
      @id = id
      @kind = kind
      @type = type
      @tags = tags
    end

  end
end