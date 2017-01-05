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
  class NetworkConnection

    attr_reader :network, :mac_address, :ip_address, :netmask, :is_connected

    # @param [String] network
    # @param [String] mac_address
    # @param [String] ip_address
    # @param [String] netmask
    # @param [String] is_connected
    def initialize(network, mac_address, ip_address, netmask, is_connected)
      @network = network
      @mac_address = mac_address
      @ip_address = ip_address
      @netmask = netmask
      @is_connected = is_connected
    end

    def ==(other)
      @network==other.network && @mac_address==other.mac_address &&
        @ip_address==other.ip_address && @netmask==other.netmask &&
        @is_connected==other.is_connected
    end
  end
end
