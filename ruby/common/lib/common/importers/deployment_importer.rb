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
  class DeploymentImporter

    # @param [String] file
    # @return [DeploymentCreateSpec]
    def self.import_file(file)
      fail EsxCloud::NotFound, "file #{file} does not exist" unless File.exist?(file)

      import_config(YAML.load_file(file))
    end

    # @param [Hash] deployment_config
    # @return [DeploymentCreateSpec]
    def self.import_config(deployment_config)
      fail UnexpectedFormat, "No deployment defined" unless deployment_config.is_a?(Hash)

      deployment = deployment_config['deployment']
      fail UnexpectedFormat, "No deployment defined" unless deployment

      deployment['use_image_datastore_for_vms'] = false unless deployment['use_image_datastore_for_vms'] == true

      image_datastores = deployment['image_datastores']
      image_datastores = image_datastores.split(/\s*,\s*/) unless image_datastores.kind_of? Array

      EsxCloud::DeploymentCreateSpec.new(
        image_datastores,
        EsxCloud::AuthConfigurationSpec.new(
          deployment['auth_enabled'],
          deployment['oauth_tenant'],
          deployment['oauth_password'],
          deployment['oauth_security_groups']),
        EsxCloud::NetworkConfigurationCreateSpec.new(
          deployment['virtual_network_enabled'],
          deployment['network_manager_address'],
          deployment['network_manager_username'],
          deployment['network_manager_password']
        ),
        EsxCloud::StatsInfo.new(
          deployment['stats_enabled'],
          deployment['stats_store_endpoint'],
          deployment['stats_store_port'],
          deployment['stats_store_type']
      ),
        deployment['syslog_endpoint'],
        deployment['ntp_endpoint'],
        deployment['use_image_datastore_for_vms'])
    end

  end
end
