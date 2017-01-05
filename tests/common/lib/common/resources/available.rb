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
  class Available

    # @return [Status]
    def self.get
      Config.client.get_status
    end

    # @param [String] json
    # @return [Available]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Status]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash)
        fail UnexpectedFormat, "Invalid Available hash: #{hash}"
      end

      new()
    end

    def initialize()
    end

    def ==(other)
    end

    def to_s
      "available: \n"
    end
  end
end
