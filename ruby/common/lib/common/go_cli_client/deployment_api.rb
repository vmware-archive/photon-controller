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
  class GoCliClient
    module DeploymentApi
      # @param [Hash] payload
      # @return [Deployment]
      def create_api_deployment(payload)
        @api_client.create_api_deployment(payload)
      end

      # @param [String] id
      # @return [Deployment]
      def deploy_deployment(id)
        @api_client.deploy_deployment(id)
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
        @api_client.delete_api_deployment(id)
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
        cmd = "deployment pause #{deployment_id}"
        run_cli(cmd)
      end

      # @param [String] deployment_id
      # @return [Boolean]
      def pause_background_tasks(deployment_id)
        cmd = "deployment pause-background-tasks #{deployment_id}"
        run_cli(cmd)
      end

      # @param [String] deployment_id
      # @return [Boolean]
      def resume_system(deployment_id)
        cmd = "deployment resume #{deployment_id}"
        run_cli(cmd)
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

      # @param [String] id
      # @param [String] payload
      def update_image_datastores(id, payload)
        cmd = "deployment update-image-datastores #{id} -d '#{payload.join(",")}'"
        run_cli(cmd)
      end
    end
  end
end
