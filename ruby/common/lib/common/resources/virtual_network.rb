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
  class VirtualNetwork

    attr_accessor :id, :name, :description, :state, :routing_type

    # @param [String] project_id
    # @param [VirtualNetworkCreateSpec] spec
    # @return [VirtualNetwork]
    def self.create(project_id, spec)
      Config.client.create_virtual_network(project_id, spec.to_hash)
    end

    # @param [String] network_id
    def self.get(network_id)
      Config.client.get_virtual_network(network_id)
    end

    # @param [String] json
    # @return [VirtualNetwork]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [VirtualNetwork]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name state routingType).to_set)
        fail UnexpectedFormat, "Invalid Virtual Network Hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["description"], hash["state"], hash["routingType"])
    end

    # @param [String] id
    # @param [String] name
    # @param [String] description
    # @param [String] state
    # @param [String] routing_type
    def initialize(id, name, description, state, routing_type)
      @id = id
      @name = name
      @description = description
      @state = state
      @routing_type = routing_type
    end

    # @return [Boolean]
    def delete
      Config.client.delete_virtual_network(@id)
    end

    def ==(other)
      @id == other.id &&
          @name == other.name &&
          @description = other.description &&
          @state = state &&
          @routing_type = other.routing_type
    end
  end
end
