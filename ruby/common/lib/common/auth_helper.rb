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
    def self.get_access_token(username, password, service_locator_url, auth_tool_path)
      if auth_tool_path.nil? || !File.exists?(auth_tool_path)
        raise EsxCloud::Error, "Could not find Auth-Token tool under #{auth_tool_path}"
      end

      tenant = extract_tenant username
      command = "java -jar #{auth_tool_path} get-access-token -t #{tenant} -a #{service_locator_url} -u " +
          Shellwords.escape(username) + " -p "  + Shellwords.escape(password)

      puts #{auth_tool_path}
      puts #{tenant}
      puts #{service_locator_url}
      puts Shellwords.escape(username)
      puts Shellwords.escape(password)
      EsxCloud::CmdRunner.run(command, true, /.*Exception:.*/)
    end

    private

    def self.extract_tenant(username)
      raise EsxCloud::Error, "User name not provided" if username.nil?

      tokens = username.split("\\")
      return tokens[0] if tokens.size == 2

      tokens = username.split("@")
      return tokens[1] if tokens.size == 2

      raise EsxCloud::Error, "User name should be provided as: 'tenant\\user' or 'user@tenant'"
    end
  end
end
