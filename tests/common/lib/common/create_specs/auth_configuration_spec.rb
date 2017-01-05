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

  # Contains info used to configure Authentication/Authorization
  class AuthConfigurationSpec

    attr_reader :enabled, :tenant, :password, :securityGroups

    # @param [Boolean] enabled
    # @param [String] tenant
    # @param [String] password
    # @param [Array<String>] securityGroups
    def initialize(enabled, tenant = nil, password = nil, securityGroups = nil)
      @enabled = enabled
      @tenant = tenant
      @password = password
      @securityGroups = securityGroups
    end

    def to_hash
      {
        enabled: @enabled,
        tenant: @tenant,
        password: @password,
        securityGroups: @securityGroups
      }
    end

    def ==(other)
      @enabled == other.enabled &&
        @tenant == other.tenant &&
        @password == other.password &&
        @securityGroups == other.securityGroups
    end

  end
end
