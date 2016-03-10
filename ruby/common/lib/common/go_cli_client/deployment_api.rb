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
        cmd = "deployment show #{id}"
        result = run_cli(cmd)
        get_deployment_from_response(result)
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
      # @return [Deployment]
      def pause_system(deployment_id)
        deployment_id = run_cli("deployment pause_system '#{deployment_id}'")
        find_deployment_by_id(deployment_id)
      end

      # @param [String] deployment_id
      # @return [Deployment]
      def pause_background_tasks(deployment_id)
        deployment_id = run_cli("deployment pause_background_tasks '#{deployment_id}'")
        find_deployment_by_id(deployment_id)
      end

      # @param [String] deployment_id
      # @return [Deployment]
      def resume_system(deployment_id)
        deployment_id = run_cli("deployment resume_system '#{deployment_id}'")
        find_deployment_by_id(deployment_id)
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
        cmd = "deployment update-image-datastores #{id} #{payload}"
        run_cli(cmd)
      end

      private

      # @param [String] result
      # @return [Deployment]
      def get_deployment_from_response(result)
        deployment_attributes = get_deployment_attributes_from_response(result)

        index = 0
        deployment_hash = Hash.new
        deployment_hash["id"]                      = deployment_attributes[index] unless deployment_attributes[index] == ""
        deployment_hash["state"]                   = deployment_attributes[index += 1] unless deployment_attributes[index] == ""
        deployment_hash["imageDatastores"]         = string_to_array(deployment_attributes[index += 1])
        deployment_hash["useImageDatastoreForVms"] = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        deployment_hash["syslogEndpoint"]          = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        deployment_hash["ntpEndpoint"]             = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        deployment_hash["loadbalancerEnabled"]     = deployment_attributes[index] unless deployment_attributes[index += 1] == ""

        authInfo_hash = Hash.new
        authInfo_hash["enabled"]        = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        authInfo_hash["username"]       = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        authInfo_hash["password"]       = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        authInfo_hash["endpoint"]       = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        authInfo_hash["tenant"]         = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        authInfo_hash["port"]           = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        authInfo_hash["securityGroups"] = string_to_array(deployment_attributes[index += 1])
        deployment_hash["auth"]         = authInfo_hash

        statsInfo_hash = Hash.new
        statsInfo_hash["enabled"]       = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        statsInfo_hash["storeEndpoint"] = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        statsInfo_hash["storePort"]     = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        deployment_hash["stats"]        = statsInfo_hash

        migrationStatus_hash = Hash.new
        migrationStatus_hash["completedCycles"]            = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        migrationStatus_hash["dataMigrationCycleProgress"] = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        migrationStatus_hash["dataMigrationCycleSize"]     = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        migrationStatus_hash["vibsUploaded"]               = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        migrationStatus_hash["vibsUploading"]              = deployment_attributes[index] unless deployment_attributes[index += 1] == ""
        deployment_hash["migration"]                       = migrationStatus_hash

        Deployment.create_from_hash(deployment_hash)
      end

      def get_deployment_attributes_from_response(result)

        # go-cli output for "photon deployment show <id>"
        # fixed-test-deployemnt-id	READY	datastore1	true	<syslogEndpoint>	<ntpEndpoint>	true
        # true	<userName>	<password>	10.118.97.154	<tenant>	0	<securityGroups>
        # false	<storeEndpoint>	0
        # 0	0	0	0	0

        values = result.split("\n")
        deployment_attributes = Array.new
        values.each { |value|
          deployment_attributes += value.split("\t")
          if value[-1] == "\t"
            deployment_attributes += [""]
          end
        }
        deployment_attributes
      end

      def string_to_array(result)
        values = Array.new
        if result.to_s != ''
          values = result.split(',')
        end
        values
      end
    end
  end
end
