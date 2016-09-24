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

    attr_accessor :id, :name, :description, :state, :routing_type, :is_default, :cidr, :low_ip_dynamic,
                  :high_ip_dynamic, :low_ip_static, :high_ip_static, :reserved_ip_list

    # @param [String] project_id
    # @param [VirtualNetworkCreateSpec] spec
    # @return [VirtualNetwork]
    def self.create(project_id, spec)
      Config.client.create_virtual_network(project_id, spec.to_hash)
    end

    # @return [VirtualNetworkList]
    def self.find_all
      Config.client.find_all_virtual_networks
    end

    # @param [String] network_id
    def self.get(network_id)
      Config.client.find_virtual_network_by_id(network_id)
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

      new(hash["id"], hash["name"], hash["description"], hash["state"], hash["routingType"], hash["isDefault"],
          hash["cidr"], hash["lowIpDynamic"], hash["highIpDynamic"], hash["lowIpStatic"], hash["highIpStatic"],
          hash["reservedIpList"])
    end

    # @return [Boolean]
    def delete
      Config.client.delete_virtual_network(@id)
    end

    # @param [String] id
    # @param [String] name
    # @param [String] description
    # @param [String] state
    # @param [String] routing_type
    # @param [String] is_default
    # @param [String] cidr
    # @param [String] low_ip_dynamic
    # @param [String] high_ip_dynamic
    # @param [String] low_ip_static
    # @param [String] high_ip_static
    # @param [Array<String>] reserved_ip_list
    def initialize(id, name, description, state, routing_type, is_default, cidr, low_ip_dynamic, high_ip_dynamic,
                   low_ip_static, high_ip_static, reserved_ip_list)
      @id = id
      @name = name
      @description = description
      @state = state
      @routing_type = routing_type
      @is_default = is_default
      @cidr = cidr
      @low_ip_dynamic = low_ip_dynamic
      @high_ip_dynamic = high_ip_dynamic
      @low_ip_static = low_ip_static
      @high_ip_static = high_ip_static
      @reserved_ip_list = reserved_ip_list
    end

    def ==(other)
      @id == other.id &&
        @name == other.name &&
        @description = other.description &&
        @state == other.state &&
        @routing_type == other.routing_type &&
        @is_default == other.is_default &&
        @cidr == other.cidr &&
        @low_ip_dynamic == other.low_ip_dynamic &&
        @high_ip_dynamic == other.high_ip_dynamic &&
        @low_ip_static == other.low_ip_static &&
        @high_ip_static == other.high_ip_static &&
        @reserved_ip_list == other.reserved_ip_list
    end
  end
end
