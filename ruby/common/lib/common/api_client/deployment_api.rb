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
  class ApiClient
    module DeploymentApi

      DEPLOYMENT_URL = "/deployment"
      DEPLOYMENTS_ROOT = "/deployments"

      # @param [String] id of manifest
      # @return [Task] task
      def create_deployment(flavor, dry_run, poll=true)
        url_path = "#{DEPLOYMENT_URL}/deploy"
        url_path += "/#{flavor}" unless flavor.nil?
        url_path += "?dryRun=true" if dry_run

        response = @http_client.post(url_path, nil)
        check_response("Started deployment", response, 201)

        task = JSON.parse(response.body)

        return task["id"] unless poll

        poll_response(response)
      end

      # @param [Hash] payload
      # @return [Deployment]
      def create_api_deployment(payload)
        response = @http_client.post_json(DEPLOYMENTS_ROOT, payload)
        check_response("Create Deployment #{payload}", response, 201)

        task = poll_response(response)
        find_deployment_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Deployment]
      def deploy_deployment(id)
        response = @http_client.post("#{DEPLOYMENTS_ROOT}/#{id}/deploy")
        check_response("Deploy Deployment '#{id}'", response, 201)

        task = poll_response(response)
        find_deployment_by_id(task.entity_id)
      end

      # @param [String] source_deployment_address
      # @param [String] destination_deployment_id
      def initialize_deployment_migration(source_deployment_address, id)
        response = @http_client.post("#{DEPLOYMENTS_ROOT}/#{id}/initialize_migration", source_deployment_address)
        check_response("Initialize Deployment Migration '#{id}'", response, 201)

        poll_response(response)
      end

      # @param [String] source_deployment_address
      # @param [String] destination_deployment_id
      def finalize_deployment_migration(source_deployment_address, id)
        response = @http_client.post("#{DEPLOYMENTS_ROOT}/#{id}/finalize_migration", source_deployment_address)
        check_response("Finalize Deployment Migration '#{id}'", response, 201)

        poll_response(response)
      end

      # @return [DeploymentList]
      def find_all_api_deployments
        response = @http_client.get(DEPLOYMENTS_ROOT)
        check_response("Find all deployments", response, 200)

        DeploymentList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_api_deployment(id)
        response = @http_client.delete("#{DEPLOYMENTS_ROOT}/#{id}")
        check_response("Delete Deployment '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @return [Deployment]
      def destroy_deployment(id)
        response = @http_client.post("#{DEPLOYMENTS_ROOT}/#{id}/destroy")
        check_response("Destroy Deployment '#{id}'", response, 201)

        task = poll_response(response)
        find_deployment_by_id(task.entity_id)
      end

      # @param [String] id
      # @param [Hash] payload
      def update_security_groups(id, payload)
        response = @http_client.post_json("#{DEPLOYMENTS_ROOT}/#{id}/set_security_groups", payload)
        check_response("Update security groups of deployment '#{id}'", response, 200)

        poll_response(response)
      end

      def find_all_deployments
        response = @http_client.get(DEPLOYMENT_URL)
        check_response("Find all deployments", response, [200, 201, 404])
        if response.code == 404
          nil
        else
          JSON.parse(response.body)
        end
      end

      def delete_deployment(poll=false)
        response = @http_client.delete(DEPLOYMENT_URL)
        check_response("Delete deployment", response, 201)

        task = JSON.parse(response.body)

        return task["id"] unless poll

        poll_response(response)
      end

      def find_all_manifests
        response = @http_client.get("#{DEPLOYMENT_URL}/manifests")
        check_response("Find all manifests", response, 200)
        JSON.parse(response.body)
      end

      def find_manifest_by_id(id)
        if id.nil?
          id = "null"
        end
        response = @http_client.get("#{DEPLOYMENT_URL}/manifest/#{id}")
        check_response("Find manifest by ID '#{id}'", response, 200)
        response.body
      end

      # @param [String] id
      # @return [Deployment]
      def find_deployment_by_id(id)
        response = @http_client.get("#{DEPLOYMENTS_ROOT}/#{id}")
        check_response("Find deployment by ID '#{id}'", response, 200)

        Deployment.create_from_json(response.body)
      end

      # @param [String] id
      # @return [VmList]
      def get_deployment_vms(id)
        response = @http_client.get("#{DEPLOYMENTS_ROOT}/#{id}/vms")
        check_response("Get VMs for deployment '#{id}'", response, 200)

        VmList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [HostList]
      def get_deployment_hosts(id)
        response = @http_client.get("#{DEPLOYMENTS_ROOT}/#{id}/hosts")
        check_response("Get hosts for deployment '#{id}'", response, 200)

        HostList.create_from_json(response.body)
      end

      # @param [String] deployment_id
      # @return [Deployment]
      def pause_system(deployment_id)
        response = @http_client.post("#{DEPLOYMENTS_ROOT}/#{deployment_id}/pause_system", nil)
        check_response("Pause system for deployment '#{deployment_id}'", response, 201)

        task = poll_response(response)
        find_deployment_by_id(task.entity_id)
      end

      # @param [String] deployment_id
      # @return [Deployment]
      def resume_system(deployment_id)
        response = @http_client.post("#{DEPLOYMENTS_ROOT}/#{deployment_id}/resume_system", nil)
        check_response("Resume system for deployment '#{deployment_id}'", response, 201)

        task = poll_response(response)
        find_deployment_by_id(task.entity_id)
      end

      # @param [String] deployment_id
      # @param [String] payload
      # @return [ClusterConfiguration]
      def enable_cluster_type(deployment_id, payload)
        response = @http_client.post_json("#{DEPLOYMENTS_ROOT}/#{deployment_id}/enable_cluster_type", payload)
        check_response("Config cluster for deployment '#{deployment_id}'", response, 200)

        ClusterConfiguration.create_from_json(response.body)
      end

      # @param [String] deployment_id
      # @param [String] payload
      # @return [Boolean]
      def disable_cluster_type(deployment_id, payload)
        puts payload
        response = @http_client.post_json("#{DEPLOYMENTS_ROOT}/#{deployment_id}/disable_cluster_type", payload)

        check_response("Delete cluster configuration for deployment '#{deployment_id}'", response, 201)

        poll_response(response)
        true
      end

    end
  end
end
