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
  class Deployment

    attr_accessor :id, :state, :image_datastores, :auth, :syslog_endpoint, :ntp_endpoint, :stats,
                  :use_image_datastore_for_vms, :loadbalancer_enabled, :migration, :cluster_configurations

    # @param[DeploymentCreateSpec] spec
    # @return [Deployment]
    def self.create(spec)
      Config.client.create_api_deployment(spec.to_hash)
    end

    # @param [String] json
    # @return [Deployment]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Deployment]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id auth imageDatastores).to_set)
        fail UnexpectedFormat, "Invalid Deployment hash: #{hash}"
      end

      if hash["clusterConfigurations"] == nil
        cluster_configurations = nil
      else
        cluster_configurations = hash["clusterConfigurations"].map { |cc| ClusterConfiguration.create_from_hash(cc)}
      end

      new(hash["id"], hash["state"], hash["imageDatastores"], AuthInfo.create_from_hash(hash["auth"]),
          StatsInfo.create_from_hash(hash["stats"]),
          hash["syslogEndpoint"], hash["ntpEndpoint"], hash["useImageDatastoreForVms"],
          hash["loadBalancerEnabled"],
          MigrationStatus.create_from_hash(hash["migrationStatus"]), cluster_configurations)
    end

    # @param [String] deployment_id
    # @return [Deployment]
    def self.find_deployment_by_id(deployment_id)
      Config.client.find_deployment_by_id deployment_id
    end

    # @return [DeploymentList]
    def self.find_all
      Config.client.find_all_api_deployments
    end

    # @param [String] deployment_id
    # @return [HostList]
    def self.get_deployment_hosts(deployment_id)
      Config.client.get_deployment_hosts deployment_id
    end

    # @param [String] source_deployment_address
    # @param [String] destination_deployment_id
    def self.initialize_deployment_migration(source_deployment_address, destination_deployment_id)
      Config.client.initialize_deployment_migration(source_deployment_address,  destination_deployment_id)
    end

    # @param [String] source_deployment_address
    # @param [String] destination_deployment_id
    def self.finalize_deployment_migration(source_deployment_address, destination_deployment_id)
      Config.client.finalize_deployment_migration(source_deployment_address,  destination_deployment_id)
    end

    # @param [String] deployment_id
    # @param [ClusterConfigurationSpec] spec
    # @return [Boolean]
    def self.enable_cluster_type(deployment_id, spec)
      Config.client.enable_cluster_type(deployment_id, spec.to_hash)
    end

    # @param [String] deployment_id
    # @param [ClusterConfigurationSpec] spec
    # @return [Boolean]
    def self.disable_cluster_type(deployment_id, spec)
      Config.client.disable_cluster_type(deployment_id, spec.to_hash)
    end

    # @param [String] id
    # @return [Boolean]
    def self.delete(id)
      Config.client.delete_api_deployment(id)
    end

    # @param [String] id
    # @param [Hash] payload
    def self.update_security_groups(id, payload)
      unless payload.is_a?(Hash) && payload.keys.to_set.superset?(Set[:items].to_set) && payload[:items].is_a?(Enumerable)
        fail UnexpectedFormat, "Invalid Security Groups hash: #{payload}"
      end

      Config.client.update_security_groups(id, payload)
    end

    # @param [String] id
    # @param [String] state
    # @param [Array<String>] image_datastores
    # @param [AuthInfo] auth
    # @param [StatsInfo] stats
    # @param [String] syslog_endpoint
    # @param [String] ntp_endpoint
    # @param [Boolean] use_image_datastore_for_vms
    # @param [Boolean] loadbalancer_enabled
    # @param [MigrationStatus] migration_status
    def initialize(id, state, image_datastores, auth, stats,
      syslog_endpoint = nil, ntp_endpoint = nil, use_image_datastore_for_vms = false, loadbalancer_enabled = true,
      migration, cluster_configurations)
      @id = id
      @state = state
      @image_datastores = image_datastores
      @auth = auth
      @stats = stats
      @syslog_endpoint = syslog_endpoint
      @ntp_endpoint = ntp_endpoint
      @use_image_datastore_for_vms = use_image_datastore_for_vms
      @loadbalancer_enabled = loadbalancer_enabled
      @migration = migration
      @cluster_configurations = cluster_configurations
    end

    def delete
      self.class.delete(@id)
    end

    def to_hash
      {
        id: @id,
        state: @state,
        imageDatastores: @image_datastores,
        auth: @auth.to_hash,
        stats: @stats.to_hash,
        syslogEndpoint: @syslog_endpoint,
        ntpEndpoint: @ntp_endpoint,
        useImageDatastoreForVms: @use_image_datastore_for_vms,
        loadbalancer_enabled: @loadbalancer_enabled,
        migrationStatus: @migration,
        clusterConfigurations: @cluster_configurations
      }
    end

    def ==(other)
      @id == other.id &&
      @state == other.state &&
      @image_datastores == other.image_datastores &&
      @auth == other.auth &&
      @stats == other.stats &&
      @syslog_endpoint == other.syslog_endpoint &&
      @ntp_endpoint == other.ntp_endpoint &&
      @use_image_datastore_for_vms == other.use_image_datastore_for_vms &&
      @migration == other.migration &&
      @cluster_configurations == other.cluster_configurations
    end
  end
end
