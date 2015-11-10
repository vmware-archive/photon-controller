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
  class AvailabilityZone

    attr_accessor :id, :name, :kind, :state

    # @param [String] id
    # @param [String] name
    # @param [String] kind
    # @param [String] state
    def initialize(id, name, kind, state)
      @id = id
      @name = name
      @kind = kind
      @state = state
    end

    def ==(other)
      @id == other.id && @name == other.name &&
      @kind == other.kind && @state == other.state
    end

    # @param[AvailabilityZoneCreateSpec] spec
    # @return [AvailabilityZone]
    def self.create(spec)
      Config.client.create_availability_zone(spec.to_hash)
    end

    # @param [String] json
    # @return [AvailabilityZone]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [AvailabilityZone]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name kind state).to_set)
        raise UnexpectedFormat, "Invalid availability_zone hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["kind"], hash["state"])
    end

    # @param [String] id
    # @return [AvailabilityZone]
    def self.find_by_id(id)
      Config.client.find_availability_zone_by_id(id)
    end

    # @param [String] name
    # @return [AvailabilityZoneList]
    def self.find_all
      Config.client.find_all_availability_zones
    end

    # @param [String] id
    # @return [Task]
    def self.delete(id)
      Config.client.delete_availability_zone(id)
    end

    # @return [Task]
    def delete
      self.class.delete(@id)
    end
  end
end
