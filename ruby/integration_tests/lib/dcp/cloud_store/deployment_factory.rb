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

require_relative "cloud_store_client"

module EsxCloud
  module Dcp
    module CloudStore
      class DeploymentFactory
        FACTORY_SERVICE_LINK = "/photon/cloudstore/deployments"
        DEPLOYMENT_SERVICE_DOC_KIND = [
            "com:vmware:photon:controller:cloudstore:dcp:entity:DeploymentService:State",
            "com:vmware:photon:controller:cloudstore:xenon:entity:DeploymentService:State"
        ]

        def self.ensure_exists(overrides = {})
          factory = DeploymentFactory.new overrides

          instance_link = factory.instance_link
          if instance_link.nil?
            CloudStoreClient.instance.post FACTORY_SERVICE_LINK, factory.payload
          else
            CloudStoreClient.instance.get "#{instance_link}"
          end
        end

        def initialize(overrides = {})
          @overrides = overrides
        end

        def instance_link
          terms = DEPLOYMENT_SERVICE_DOC_KIND.map do |kind|
            {
                "term" => {
                  "matchValue" => kind,
                  "propertyName" => "documentKind",
                },
                "occurance" => "SHOULD_OCCUR",
            }
          end

          response = CloudStoreClient.instance.query terms, true
          return nil if response["results"]["documentLinks"].size == 0
          fail "More than one deployment found" if response["results"]["documentLinks"].size > 1

          response["results"]["documentLinks"].first
        end

        def payload
          {}
            .merge state_settings
            .merge image_settings
            .merge oauth_settings
            .merge syslog_settings
            .merge ntp_settings
            .merge stats_settings
            .merge chairman_settings
            .merge document_self_link
            .merge @overrides
            .keep_if { |_k, v| !v.nil? }
        end

        private
        def ip
          ENV["PUBLIC_NETWORK_IP"] || ENV["PRIVATE_NETWORK_IP"] || "172.31.253.66"
        end

        def state_settings
          {
            state: "READY"
          }
        end

        def image_settings
          datastores = ENV["ESX_DATASTORE"] || "datastore1"
          datastore_list = datastores.split(",")
          {
            imageDataStoreNames: datastore_list,
            imageDataStoreUsedForVMs: true
          }
        end

        def oauth_settings
          oauth_enabled = !ENV["ENABLE_AUTH"].nil?
          settings = {
            oAuthEnabled: oauth_enabled
          }

          if oauth_enabled
            tenant = ENV["PHOTON_AUTH_SERVER_TENANT"]

            groups = ENV["PHOTON_AUTH_ADMIN_GROUPS"].split(",") if ENV["PHOTON_AUTH_ADMIN_GROUPS"]
            groups << "#{tenant}\\Administrators"

            settings.merge!(
              oAuthTenantName: tenant,
              oAuthUserName: ENV["PHOTON_USERNAME_ADMIN"],
              oAuthPassword: ENV["PHOTON_PASSWORD_ADMIN"],
              oAuthServerAddress: ENV["PHOTON_AUTH_LS_ENDPOINT"] || ip,
              oAuthServerPort: ENV["PHOTON_AUTH_SERVER_PORT"] || "443",
              oAuthSecurityGroups: groups || []
            )
          end

          settings
        end

        def syslog_settings
          {
            syslogEndpoint: ENV["SYSLOG_ENDPOINT"]
          }
        end

        def ntp_settings
          {
            ntpEndpoint: ENV["NTP_ENDPOINT"]
          }
        end

        def stats_settings
          {
            statsStoreEndpoint: ENV["STATS_STORE_ENDPOINT"],
            statsStorePort: ENV["STATS_STORE_PORT"],
            statsEnabled: ENV["STATS_ENABLED"]
          }
        end

        def chairman_settings
          chairman_ip = ENV["ESXCLOUD_CHAIRMAN_IP"] || ip
          {
            chairmanServerList: ["#{chairman_ip}:13000"]
          }
        end

        def document_self_link
          {
              documentSelfLink: ENV["RANDOM_GENERATED_DEPLOYMENT_ID"] || "test-deployment"
          }
        end
      end
    end
  end
end

