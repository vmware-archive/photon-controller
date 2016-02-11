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

require "shellwords"

require "net/http"
require "uri"
require "openssl"

module EsxCloud
  class AuthHelper

    # @param [String] username
    # @param [String] password
    # @param [String] url of the authentication service
    # @return [String] access_token
    def self.get_access_token(username, password, service_locator_url)
      AuthHelper.verify_username username
      uri = URI.parse("https://#{service_locator_url}:443/openidconnect/token")
      https = Net::HTTP.new(uri.host, uri.port)
      https.use_ssl = true
      https.verify_mode = OpenSSL::SSL::VERIFY_NONE
      response = https.post(uri.path, "username=#{username}&password=#{password}&grant_type=password&scope=openid offline_access id_groups at_groups rs_admin_server")

      puts "\n\n\n\n\nautho token"
      puts uri.host
      puts uri.port
      puts uri.path
      puts "username=#{username}&password=#{password}&grant_type=password&scope=openid offline_access id_groups at_groups rs_admin_server"
      puts JSON.parse(response.body)["access_token"]
      puts "\n\n\n\n\n\n\n"

      JSON.parse(response.body)["access_token"]
    end

    private

    def self.verify_username(username)
      raise EsxCloud::Error, "User name not provided" if username.nil?

      tokens = username.split("\\")
      return if tokens.size == 2

      tokens = username.split("@")
      return if tokens.size == 2

      raise EsxCloud::Error, "User name should be provided as: 'tenant\\user' or 'user@tenant'"
    end
  end
end
