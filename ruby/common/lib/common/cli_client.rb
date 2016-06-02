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

require_relative "cli_client/auth_api"
require_relative "cli_client/deployment_api"
require_relative "cli_client/portgroup_api"
require_relative "cli_client/project_api"
require_relative "cli_client/resource_ticket_api"
require_relative "cli_client/tenant_api"
require_relative "cli_client/vm_api"
require_relative "cli_client/disk_api"
require_relative "cli_client/flavor_api"
require_relative "cli_client/image_api"
require_relative "cli_client/import_api"
require_relative "cli_client/status_api"
require_relative "cli_client/network_api"
require_relative "cli_client/task_api"
require_relative "cli_client/host_api"
require_relative "cli_client/cluster_api"
require_relative "cli_client/availability_zone_api"
require_relative "cli_client/virtual_network_api"

module EsxCloud
  class CliClient
    include EsxCloud::CliClient::AuthApi
    include EsxCloud::CliClient::DeploymentApi
    include EsxCloud::CliClient::PortGroupApi
    include EsxCloud::CliClient::ProjectApi
    include EsxCloud::CliClient::ResourceTicketApi
    include EsxCloud::CliClient::TenantApi
    include EsxCloud::CliClient::VmApi
    include EsxCloud::CliClient::DiskApi
    include EsxCloud::CliClient::FlavorApi
    include EsxCloud::CliClient::ImageApi
    include EsxCloud::CliClient::ImportApi
    include EsxCloud::CliClient::StatusApi
    include EsxCloud::CliClient::NetworkApi
    include EsxCloud::CliClient::TaskApi
    include EsxCloud::CliClient::HostApi
    include EsxCloud::CliClient::ClusterApi
    include EsxCloud::CliClient::AvailabilityZoneApi
    include EsxCloud::CliClient::VirtualNetworkApi

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
    end

    # @param [String] command
    # @return [String]
    def run_cli(command)
      cmd = "#{@cli_path} --target '#{@target_url}' --non-interactive #{command}"
      EsxCloud::CmdRunner.run(cmd)
    end

  end
end
