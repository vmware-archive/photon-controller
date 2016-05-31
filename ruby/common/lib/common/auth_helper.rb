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

module EsxCloud
  class AuthHelper

    # @param [String] username
    # @param [String] password
    # @param [String] url of the authentication service
    # @param [String] absolute path to the auth-token tool.
    # @return [String] access_token
    def self.get_access_token(username, password, service_locator_url, auth_tool_path = nil)
      token_path = "/openidconnect/token"
      password_grant_format_string = "grant_type=password&username=#{username}&password=#{password}&scope=openid"

      http_client = HttpClient.new(service_locator_url)
      http_client.post(token_path, password_grant_format_string,
                       {"Content-Type" => "application/x-www-form-urlencoded"});
    end

  end
end
