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
      image_datastores = deployment['image_datastores'].split(/\s*,\s*/)
      EsxCloud::DeploymentCreateSpec.new(
        image_datastores,
        EsxCloud::AuthInfo.new(
          deployment['auth_enabled'] == true,
          deployment['oauth_endpoint'],
          deployment['oauth_port'],
          deployment['oauth_tenant'],
          deployment['oauth_username'],
          deployment['oauth_password'],
          deployment['oauth_security_groups']),
        deployment['syslog_endpoint'],
        deployment['ntp_endpoint'],
        deployment['use_image_datastore_for_vms'])
    end

  end
end
