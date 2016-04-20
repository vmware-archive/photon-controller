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

require_relative "go_cli_client/auth_api"
require_relative "go_cli_client/cluster_api"
require_relative "go_cli_client/deployment_api"
require_relative "go_cli_client/disk_api"
require_relative "go_cli_client/flavor_api"
require_relative "go_cli_client/host_api"
require_relative "go_cli_client/image_api"
require_relative "go_cli_client/import_api"
require_relative "go_cli_client/network_api"
require_relative "go_cli_client/portgroup_api"
require_relative "go_cli_client/project_api"
require_relative "go_cli_client/resource_ticket_api"
require_relative "go_cli_client/status_api"
require_relative "go_cli_client/task_api"
require_relative "go_cli_client/tenant_api"
require_relative "go_cli_client/vm_api"
require_relative "go_cli_client/availability_zone_api"
require_relative "go_cli_client/available_api"

module EsxCloud
  class GoCliClient
    include EsxCloud::GoCliClient::AuthApi
    include EsxCloud::GoCliClient::ClusterApi
    include EsxCloud::GoCliClient::DeploymentApi
    include EsxCloud::GoCliClient::DiskApi
    include EsxCloud::GoCliClient::FlavorApi
    include EsxCloud::GoCliClient::HostApi
    include EsxCloud::GoCliClient::ImageApi
    include EsxCloud::GoCliClient::ImportApi
    include EsxCloud::GoCliClient::NetworkApi
    include EsxCloud::GoCliClient::PortGroupApi
    include EsxCloud::GoCliClient::ProjectApi
    include EsxCloud::GoCliClient::ResourceTicketApi
    include EsxCloud::GoCliClient::StatusApi
    include EsxCloud::GoCliClient::TaskApi
    include EsxCloud::GoCliClient::TenantApi
    include EsxCloud::GoCliClient::VmApi
    include EsxCloud::GoCliClient::AvailabilityZoneApi
    include EsxCloud::GoCliClient::AvailableApi

    attr_reader :project_to_tenant, :vm_to_project, :disk_to_project, :api_client

    # @param [String] cli_path
    # @param [String] target_url
    # @param [String] access_token
    def initialize(cli_path, target_url, access_token = nil)
      @cli_path = cli_path
      @target_url = target_url
      @api_client = EsxCloud::ApiClient.new(target_url, nil, access_token)

      @project_to_tenant = {}
      @vm_to_project = {}
      @disk_to_project = {}

      unless File.executable?(@cli_path)
        raise ArgumentError, "'#{@cli_path}' file should exist and be executable"
      end

      cmd = "#{@cli_path} target set --nocertcheck #{@target_url}"
      EsxCloud::CmdRunner.run(cmd)
    end

    # @param [String] command
    # @return [String]
    def run_cli(command)
      log_file = ""
      unless EsxCloud::Config.log_file_name.to_s.empty?
        log_file = " --log-file #{EsxCloud::Config.log_file_name}"
      end
      cmd = "#{@cli_path} --non-interactive #{log_file} #{command}"
      EsxCloud::CmdRunner.run(cmd)
    end

  end
end
