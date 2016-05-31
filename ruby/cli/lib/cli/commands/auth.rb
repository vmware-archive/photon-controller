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

module EsxCloud::Cli
  module Commands
    class Auth < Base

      usage "auth show"
      desc "Get Authentication/ Authorization Information"
      def show(args = [])
        initialize_client
        auth_info = EsxCloud::AuthInfo.get
        display_auth_info(auth_info)
      end

      usage "auth login <options>"
      desc "Login a user"
      def login(args = [])

        username, password, access_token = nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-u", "--username NAME", "Login Username") { |u| username = u }
          opts.on("-p", "--password NAME", "Login Password") { |p| password = p }
          opts.on("-a", "--access_token TOKEN", "Access token (do not re-authenticate)") { |a| access_token = a }
        end

        parse_options(args, opts_parser)

        # Get the serviceLocatorUrl by calling into API-Frontend Auth API 
        initialize_client
        auth_info = EsxCloud::AuthInfo.get
        unless auth_info.enabled
          puts green("Authentication not enabled. No need to login.")
          return
        end

        if interactive?
          username ||= ask("Login username: ")
          password ||= ask("Login password: ") { |q| q.echo = "*" } if access_token.blank?
        end

        if username.blank? || (password.blank? && access_token.blank?)
          usage_error("Please provide username and password or access token", opts_parser)
        end

        # Check if there was another user previously logged in. If so, logout the user.
        if !cli_config.access_token.blank?
          puts "Clearing cached login credentials ..."
          cli_config.user_name = nil
          cli_config.access_token = nil
          cli_config.save
        end

        # Retrieve access token if necessary
        if access_token.blank?
          # Get the access_token for the provided user credentials.
          puts "Logging in #{username} ..."

          https_header = "https://"
          access_token = EsxCloud::AuthHelper.get_access_token(
              username, password, "#{https_header}#{auth_info.endpoint}")
        end

        # Write the token in the Config and save it for future use. 
        cli_config.user_name = username
        cli_config.access_token = access_token
        cli_config.save

        puts green("#{username} logged-in successfully")
      end

      usage "auth logout"
      desc "Logout the currently logged-in user."
      def logout(args = [])
        if cli_config.access_token.blank?
          usage_error("Fail to logout. No user is currently logged in.")
        end

        username = cli_config.user_name
        cli_config.user_name = nil
        cli_config.access_token = nil
        cli_config.save
        puts green("Logout #{username} success.")
      end

      private

      def assets_folder
        File.join(
                File.dirname(File.expand_path(__FILE__)),
                "../../../assets")
      end

      def get_auth_tool_path
        auth_tool_files = Dir.glob(File.join(assets_folder, "auth-tool-runnable*.jar"))

        if auth_tool_files.length == 0
          raise EsxCloud::CliError, "Could not find Auth-Token tool under #{assets_folder}"
        elsif auth_tool_files.length > 1
          raise EsxCloud::CliError, "More than one Auth-Token tool were found under #{assets_folder}"
        end

        auth_tool_files[0]
      end

      def display_auth_info(auth_info)
        puts "Enabled:  #{auth_info.enabled}"
        puts "Endpoint: #{auth_info.endpoint}"
        puts "Port:     #{auth_info.port || '-'}"
      end
    end
  end
end
