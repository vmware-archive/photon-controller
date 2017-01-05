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
  class Status

    attr_reader :status, :components

    # @return [Status]
    def self.get
      Config.client.get_status
    end

    # @param [String] json
    # @return [Status]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Status]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(status components).to_set)
        fail UnexpectedFormat, "Invalid STATUS hash: #{hash}"
      end

      new(hash["status"], hash["components"])
    end

    # @param [String] status
    # @param [Array<ComponentStatus>] components
    def initialize(status, components)
      @status = status
      @components = components.map do |component|
        ComponentStatus.create_from_hash(component)
      end
    end

    def ==(other)
      @status == other.status && @components == other.components
    end

    def to_s
      "status: #{status}\n" +
        "components:\n" +
        components.map { |c| c.to_s }.join("\n")
    end
  end
end
