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
  class Network

    attr_accessor :id, :name, :description, :state, :portgroups, :is_default

    # @param[NetworkCreateSpec] spec
    # @return [Network]
    def self.create(spec)
      Config.client.create_network(spec.to_hash)
    end

    # @param [String] network_id
    # @return [Boolean]
    def self.delete(network_id)
      Config.client.delete_network(network_id)
    end

    # @param [String] network_id
    # @return [Network]
    def self.find_network_by_id(network_id)
      Config.client.find_network_by_id(network_id)
    end

    # @return [ImageList]
    def self.find_all
      Config.client.find_all_networks
    end

    # @return [ImageList]
    def self.find_by_name(name)
      Config.client.find_networks_by_name(name)
    end

    # @param [String] json
    # @return [Network]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Network]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name portGroups).to_set)
        fail UnexpectedFormat, "Invalid Network hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["description"], hash["state"], hash["portGroups"], hash["isDefault"])
    end

    # @return [Boolean]
    def delete
      self.class.delete(@id)
    end

    # @param [String] id
    # @param [String] name
    # @param [String] description
    # @param [String] state
    # @param [Array<String>] portgroups
    # @param [Boolean] is_default
    def initialize(id, name, description, state, portgroups, is_default)
      @id = id
      @name = name
      @description = description
      @state = state
      @portgroups = portgroups
      @is_default = is_default
    end

    def ==(other)
      @id == other.id &&
        @name == other.name &&
        @description == other.description &&
        @state == other.state &&
        @portgroups == other.portgroups &&
        @is_default == other.is_default
    end

  end
end
