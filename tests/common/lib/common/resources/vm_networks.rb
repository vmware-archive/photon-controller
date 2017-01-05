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
  class VmNetworks

    attr_reader :network_connections

    # @param [Task] task
    # @return [VmNetworks]
    def self.create_from_task(task)
      resource_properties = task.resource_properties
      networks = resource_properties["networkConnections"]
      unless networks.is_a?(Array)
        fail UnexpectedFormat, "Invalid networks: #{networks.inspect}"
      end
      networks.each do |network|
        unless network.is_a?(Hash)
          fail UnexpectedFormat, "Invalid network format: #{network.inspect}"
        end
      end

      return VmNetworks.create_from_hash_array(networks)
    end

    # @param [Array] networks
    # @return [VmNetworks]
    def self.create_from_hash_array(networks)
      unless networks.is_a?(Array)
        fail UnexpectedFormat, "Invalid hash_array: #{networks.inspect}"
      end
      network_connections = networks.map do |network|
        unless network.is_a?(Hash)
          fail UnexpectedFormat, "Invalid network: #{network.inspect}"
        end
        NetworkConnection.new(network["network"],
                             network["macAddress"],
                             network["ipAddress"],
                             network["netmask"],
                             network["isConnected"])
      end

      new(network_connections)
    end

    # @param [Hash] network_connections
    def initialize(network_connections)
      @network_connections = network_connections
    end
  end
end
