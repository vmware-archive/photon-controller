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

  # Contains Authentication/ Authorization Info
  class AuthInfo

    attr_reader :enabled, :endpoint, :port, :tenant, :username, :password, :securityGroups

    # @return [AuthInfo]
    def self.get
      Config.client.get_auth_info
    end

    # @param [String] json
    # @return [AuthInfo]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [AuthInfo]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(enabled).to_set)
        fail UnexpectedFormat, "Invalid AuthInfo hash: #{hash}"
      end

      new(hash["enabled"], hash["endpoint"], hash["port"], hash["tenant"], hash["username"], hash["password"], hash["securityGroups"])
    end

    # @param [Boolean] enabled
    # @param [String] endpoint
    # @param [String] port
    # @param [String] tenant
    # @param [String] username
    # @param [String] password
    def initialize(enabled, endpoint = nil, port = nil,
      tenant = nil, username = nil, password = nil, securityGroups = nil)
      @enabled = enabled
      @endpoint = endpoint
      @port = port
      @tenant = tenant
      @username = username
      @password = password
      @securityGroups = securityGroups
    end

    # @param [AuthInfo] other
    def ==(other)
      @enabled == other.enabled &&
        @endpoint == other.endpoint &&
        @port == other.port &&
        @tenant == other.tenant &&
        @username == other.username &&
        @password == other.password &&
        @securityGroups == other.securityGroups
    end

    def to_hash
      {
        enabled: @enabled,
        endpoint: @endpoint,
        port: @port,
        tenant: @tenant,
        username: @username,
        password: @password,
        securityGroups: @securityGroups
      }
    end

  end
end
