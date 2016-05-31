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
      protocol = args[:protocol] unless args.nil?
      address = args[:address] unless args.nil?
      port = args[:port] unless args.nil?

      if ENV["ENABLE_AUTH"] && ENV["ENABLE_AUTH"] == "true"
         protocol ||= "https"
         port ||= "443"
      else
         protocol ||= "http"
         port ||= "28080"
      end

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
      service_locator_url = ENV["PHOTON_AUTH_LS_ENDPOINT"].strip

      username = ENV["PHOTON_USERNAME_#{user_suffix}"].strip
      puts "username: #{username}"
      password = ENV["PHOTON_PASSWORD_#{user_suffix}"].strip
      puts "password: #{password}"

      https_header = "https://"
      EsxCloud::AuthHelper.get_access_token(
          username, password, "#{https_header}#{service_locator_url}")
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
        client.run_cli("target login -t #{token}") if token
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
