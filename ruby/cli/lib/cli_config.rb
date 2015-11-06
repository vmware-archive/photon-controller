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
  module CliConfig
    class << self

      CONFIG_FILE = File.expand_path("~/.photon")

      attr_reader :commands, :client

      def init
        @commands = []
        @config = read_from_file
        @interactive = true
      end

      # @param [Commands::Base] command
      def register_command(command)
        @commands << command
      end

      def client
        @client ||= get_client
      end

      def interactive=(is_interactive)
        @interactive = is_interactive
      end

      def interactive?
        @interactive
      end

      # @param [EsxCloud::Tenant] tenant
      def tenant=(tenant)
        if tenant
          @config["tenant"] = {"id" => tenant.id, "name" => tenant.name}
        else
          @config.delete("tenant")
        end
        self.project = nil
      end

      def tenant
        return nil if @config["tenant"].nil?
        client.find_tenant_by_id(@config["tenant"]["id"])
      end

      # @param [String] token
      def access_token=(token)
        @config["access_token"] = {"token" => token}
      end

      def access_token
        return nil if @config["access_token"].nil?
        @config["access_token"]["token"]
      end

      # @param [String] name
      def user_name=(name)
        @config["user"] = {"name" => name}
      end

      def user_name
        return nil if @config["user"].nil?
        @config["user"]["name"]
      end

      # @param [String] url
      def target=(url)
        @config["target"] = url
      end

      def target
        @config["target"]
      end

      # @param [EsxCloud::Project] project
      def project=(project)
        if project
          @config["project"] = {"id" => project.id, "name" => project.name}
        else
          @config.delete("project")
        end
      end

      def project
        return nil if @config["project"].nil?
        client.find_project_by_id(@config["project"]["id"])
      end

      # @param [Integer] threads
      def demo_threads=(threads)
        if threads
          @config["demo_threads"] = threads
        else
          @config.delete("demo_threads")
        end
      end

      def demo_threads
        Integer(@config["demo_threads"]) if @config["demo_threads"]
      end

      def read_from_file
        YAML.load_file(CONFIG_FILE)
      rescue Errno::ENOENT
        {}
      end

      def save
        File.open(CONFIG_FILE, "w+") do |f|
          YAML.dump(@config, f)
        end
      end

      private

      def get_client
        if target.blank?
          puts red("target is not set!")
          abort
        end

        unless target.start_with?("http")
          puts red("target #{target} does not start with 'http'")
          abort
        end

        puts "Using target '#{target}'"
        task_tracker = EsxCloud::Cli::TaskTracker.new
        EsxCloud::Config.client = EsxCloud::ApiClient.new(target, task_tracker, access_token)
      end

    end
  end
end
