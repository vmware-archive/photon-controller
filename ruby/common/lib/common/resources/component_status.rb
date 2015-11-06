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
  class ComponentStatus

    attr_accessor :name, :status, :instances, :message, :stats

    # @param [String] name
    # @param [String] status
    # @param [Array<ComponentInstance>] instances
    # @param [String] message
    # @param [Hash] stats
    def initialize(name, status, instances = [], message = nil, stats = nil)
      @name = name
      @status = status
      @message = message
      @stats = stats
      @instances = instances.map do |instance|
        ComponentInstance.create_from_hash(instance)
      end
    end

    def ==(other)
      @name == other.name &&
        @status == other.status &&
        @instances == other.instances &&
        @message == other.message &&
        @stats == other.stats
    end

    # @param [String] json
    # @return [ComponentStatus]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [ComponentStatus]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(component status).to_set)
        fail UnexpectedFormat, "Invalid component status hash: #{hash}"
      end

      new(hash["component"], hash["status"], hash["instances"], hash["message"], hash["stats"])
    end

    def to_s
      "#{name}: #{status}, message: #{message}, stats: #{stats}\n" +
        "instances:\n" +
        instances.map { |i| i.to_s }.join("\n")
    end

  end
end