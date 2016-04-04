# Copyright 2016 VMware, Inc. All Rights Reserved.
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

  # Contains info used to configure network
  class NetworkConfigurationCreateSpec
    attr_reader :virtualNetworkEnabled, :networkManagerAddress, :networkManagerUsername, :networkManagerPassword

    # @param [Boolean] virtualNetworkEnabled
    # @param [String] networkManagerAddress
    # @param [String] networkManagerUsername
    # @param [String] networkManagerPassword
    def initialize(virtualNetworkEnabled, networkManagerAddress = nil, networkManagerUsername = nil,
                   networkManagerPassword = nil)
      @virtualNetworkEnabled = virtualNetworkEnabled
      @networkManagerAddress = networkManagerAddress
      @networkManagerUsername = networkManagerUsername
      @networkManagerPassword = networkManagerPassword
    end

    def to_hash
      {
        virtualNetworkEnabled: @virtualNetworkEnabled,
        networkManagerAddress: @networkManagerAddress,
        networkManagerUsername: @networkManagerUsername,
        networkManagerPassword: @networkManagerPassword
      }
    end

    def ==(other)
      @virtualNetworkEnabled == other.virtualNetworkEnabled &&
      @networkManagerAddress == other.networkManagerAddress &&
      @networkManagerUsername = other.networkManagerUsername &&
      @networkManagerPassword == other.networkManagerPassword
    end

  end
end
