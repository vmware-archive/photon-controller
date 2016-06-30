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
  class VirtualNetworkCreateSpec
    attr_accessor :name, :description, :routing_type, :dhcp_enabled, :size

    # @param [String] name
    # @param [String] description
    # @param [String] routing_type
    # @param [Boolean] dhcp_enabled
    # @param [int] size
    def initialize(name, description, routing_type, dhcp_enabled, size)
      @name = name
      @description = description
      @routing_type = routing_type
      @dhcp_enabled = dhcp_enabled
      @size = size
    end

    def to_hash
      {
        name: @name,
        description: @description,
        routingType: @routing_type,
        dhcpEnabled: @dhcp_enabled,
        size: @size
      }
    end
  end
end
