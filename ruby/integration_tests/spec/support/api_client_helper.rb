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

require "zookeeper"

class ApiClientHelper

  class << self

    def driver
      (ENV["DRIVER"] ||= "api").downcase
    end

    def management(args = nil)
      protocol = args.nil? ? "https" : args[:protocol] || "https"
      address = args[:address] unless args.nil?
      port = args.nil? ? (ENV["API_FE_PORT"] || "443") : (args[:port] || ENV["API_FE_PORT"] || "443").strip
      setup_client(protocol, address, port)
    end

    def zookeeper
      zk_address = (ENV["ZOOKEEPER_ADDRESS"] || "172.31.253.66").strip
      Zookeeper.new("#{zk_address}:#{(ENV["ZOOKEEPER_PORT"] || "2181").strip}")
    end

    def postgres_connection_string
      {
        host: ENV["PUBLIC_NETWORK_IP"] || "172.31.253.66",
        port: 5432,
        dbname: ENV["POSTGRES_DBNAME"] || 'esxclouddb',
        user:  ENV["POSTGRES_USER"] || 'esxcloud',
        password:  ENV["POSTGRES_PASSWORD"] || 'esxcloud'
      }
    end

    def endpoint(protocol = nil, address = nil, port = nil)
      protocol ||= (ENV["API_FE_PORT"] ? "https" : "http")
      address ||= (ENV["API_ADDRESS"] || "172.31.253.66").strip
      port ||= (ENV["API_FE_PORT"] || "9000").strip

      "#{protocol}://#{address}:#{port}"
    end

    def access_token(user_suffix = "ADMIN")
      auth_tool_path = nil
      if ENV["PHOTON_AUTH_TOOL_PATH"].nil? || (ENV["PHOTON_AUTH_TOOL_PATH"]).strip.empty?
        auth_tool_path = Dir.glob(File.join(File.dirname(File.expand_path(__FILE__)),
                                            "../../../cli/assets/auth-tool-runnable*.jar"))[0]
      else
        auth_tool_path = ENV["PHOTON_AUTH_TOOL_PATH"].strip
      end

      service_locator_url = ENV["PHOTON_AUTH_LS_ENDPOINT"].strip

      username = ENV["PHOTON_USERNAME_#{user_suffix}"].strip
      password = ENV["PHOTON_PASSWORD_#{user_suffix}"].strip

      EsxCloud::AuthHelper.get_access_token(
        username, password, service_locator_url, auth_tool_path)
    end

    private

    def setup_client(protocol, address, port)
      # Extract environment variable information and retrieve access token.
      if get_env_var("ENABLE_AUTH", false) == "true"
        username = ENV["PHOTON_USERNAME_ADMIN"].strip
        token = access_token
      end

      # Create test driver.
      api_address = endpoint(protocol, address, port)
      client = nil

      if driver == "cli" # CLI driver
        cli_path = File.expand_path(File.join(File.dirname(__FILE__), "../../../cli/bin/photon"))
        client = EsxCloud::CliClient.new(cli_path, api_address, token)
        client.run_cli("auth login -u #{username} -a #{token}") if token
      elsif driver == "api" # API driver
        client = EsxCloud::ApiClient.new(api_address, nil, token)
      elsif driver == "gocli" # Go CLI driver
        go_cli_path = ENV["GO_CLI_PATH"].strip
        client = EsxCloud::GoCliClient.new(go_cli_path, api_address, token)
        client.run_cli("target login #{token}") if token
      else # Unknown driver.
        fail "Unknown driver '#{driver}'. Only 'cli', 'gocli' and 'api' drivers are supported."
      end

      client
    end

    def get_env_var(varname, required = true)
      env_var_value = ENV[varname]
      env_var_value || (not required) || fail("#{varname} is required.")

      env_var_value
    end
  end

end
