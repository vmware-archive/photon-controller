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
  class CliClient
    module DeploymentApi
      # @param [Hash] payload
      # @return [Deployment]
      def create_api_deployment(payload)
        syslog_endpoint = payload[:syslogEndpoint]
        ntp_endpoint = payload[:ntpEndpoint]
        image_datastores = payload[:imageDatastores]
        use_image_datastore_for_vms = payload[:useImageDatastoreForVms]
        auth_info = payload[:auth]
        auth_enabled = auth_info[:enabled]
        oauth_endpoint = auth_info[:endpoint]
        oauth_port = auth_info[:port]
        oauth_tenant = auth_info[:tenant]
        oauth_username = auth_info[:username]
        oauth_password = auth_info[:password]
        oauth_security_groups = auth_info[:securityGroups]
        loadbalancer_enabled = payload["loadblanacerEnabled"]
        stats_info= payload[:stats]
        stats_enabled = stats_info[:enabled]
        stats_store_endpoint = stats_info[:storeEndpoint]
        stats_store_port = stats_info[:storePort]

        cmd = "deployment create"
        cmd += " -i '#{image_datastores.join(",")}'" if image_datastores
        cmd += " -s '#{syslog_endpoint}'" if syslog_endpoint
        cmd += " -n '#{ntp_endpoint}'" if ntp_endpoint
        cmd += " -d" if stats_enabled
        cmd += " -e '#{stats_store_endpoint}'" if stats_store_endpoint
        cmd += " -f '#{stats_store_port}'" if stats_store_port
        cmd += " -v" if use_image_datastore_for_vms
        cmd += " -a" if auth_enabled
        cmd += " -o '#{oauth_endpoint}'" if oauth_endpoint
        cmd += " -r '#{oauth_port}'" if oauth_port
        cmd += " -t '#{oauth_tenant}'" if oauth_tenant
        cmd += " -u '#{oauth_username}'" if oauth_username
        cmd += " -p '#{oauth_password}'" if oauth_password
        cmd += " -g '#{oauth_security_groups.join(",")}'" if oauth_security_groups
        cmd += " -l" if loadbalancer_enabled

        run_cli(cmd)
        deployments = find_all_api_deployments.items
        if deployments.size > 1
          fail EsxCloud::CliError, "There are more than one Deployment."
        end

        deployments.first
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Deployment]
      def deploy_deployment(id, payload = {})
        @api_client.deploy_deployment(id, payload)
      end

      # @param [String] id
      # @return [Deployment]
      def find_deployment_by_id(id)
        @api_client.find_deployment_by_id(id)
      end

      # @return [DeploymentList]
      def find_all_api_deployments
        @api_client.find_all_api_deployments
      end

      # @param [String] source_deployment_address
      # @param [String] id
      def initialize_deployment_migration(source_deployment_address, id)
        @api_client.initialize_deployment_migration(source_deployment_address, id)
      end

      # @param [String] source_deployment_address
      # @param [String] id
      def finalize_deployment_migration(source_deployment_address, id)
        @api_client.finalize_deployment_migration(source_deployment_address, id)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_api_deployment(id)
        run_cli("deployment delete '#{id}'")
        true
      end

      # @param [String] id
      # @return [Deployment]
      def destroy_deployment(id)
        @api_client.destroy_deployment(id)
      end

      # @param [String] id
      # @return [VmList]
      def get_deployment_vms(id)
        @api_client.get_deployment_vms(id)
      end

      # @param [String] id
      # @return [HostList]
      def get_deployment_hosts(id)
        @api_client.get_deployment_hosts(id)
      end

      # @param [String] id
      # @param [Hash] payload
      def update_security_groups(id, payload)
        @api_client.update_security_groups(id, payload)
      end

      # @param [String] deployment_id
      # @return [Boolean]
      def pause_system(deployment_id)
        cmd = "deployment pause_system #{deployment_id}"
        run_cli(cmd)
        true
      end

      # @param [String] deployment_id
      # @return [Boolean]
      def pause_background_tasks(deployment_id)
        cmd = "deployment pause_background_tasks #{deployment_id}"
        run_cli(cmd)
        true
      end

      # @param [String] deployment_id
      # @return [Boolean]
      def resume_system(deployment_id)
        cmd = "deployment resume_system #{deployment_id}"
        run_cli(cmd)
        true
      end

      # @param [String] deployment_id
      # @param [String] payload
      # @return [ClusterConfiguration]
      def enable_cluster_type(deployment_id, payload)
        @api_client.enable_cluster_type(deployment_id, payload)
      end

      # @param [String] deployment_id
      # @param [String] payload
      # @return [Boolean]
      def disable_cluster_type(deployment_id, payload)
        @api_client.disable_cluster_type(deployment_id, payload)
      end
    end
  end
end
