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

require_relative "../../../lib/integration"

module EsxCloud
  class DeployerClient
    DEFAULT_CONTENT_TYPE = "application/json"
    DCP_ROOT_PATH = "/provisioning/esxcloud"

    # Service base paths
    DEPLOYMENT_SERVICE_BASE_PATH="/deployments"
    ESXHYPERVISOR_SERVICE_BASE_PATH="/esx-hypervisors"
    DUMMY_SERVICE_BASE_PATH = "/dummies"
    UPLOAD_VIB_TASK_SERVICE_BASE_PATH = "/vib-uploads"

    def initialize(endpoint)
      @endpoint = endpoint.chomp("/")
      @http_client = HttpClient.new(endpoint)
      @created_services = []
    end

    def create_service(service_base_link, payload)
      response = @http_client.post_json("#{DCP_ROOT_PATH}#{service_base_link}", payload)
      return nil if response.code != 200

      body = JSON.parse(response.body)
      service_link = body["documentSelfLink"]
      @created_services << service_link
      return service_link
    end

    def get_document(service_base_link)
      response = get_service("#{DCP_ROOT_PATH}#{service_base_link}")
      JSON.parse(response.body)
    end

    def get_service(service_link)
      @http_client.get(service_link)
    end

    def delete_service(service_link)
      @http_client.delete(service_link, nil, {}, {}) # send a {} in body to actually delete the object
      @created_services.delete(service_link)
    end

    def patch_service(service_link, payload)
      @http_client.patch_json(service_link, payload)
    end

    def poll_task(service_link, target_state, timeout = 1, retries = 5, backoff = 1)
      retries.times do
        response = get_service(service_link)
        if response.code == 200
          body = JSON.parse(response.body)
          if [target_state, "FINISHED", "FAILED", "CANCELED"].include? body["taskState"]["stage"]
            return body
          end
        end

        sleep timeout
        timeout *= backoff
      end

      return nil
    end

    def create_esxhypervisor_entity(usage_tags = ["MGMT"])
      {
          hostAddress: TestHelpers.get_esx_ip,
          userName: TestHelpers.get_esx_username,
          password: TestHelpers.get_esx_password,
          usageTags: usage_tags,
          metadata: {
            MANAGEMENT_DATASTORE: TestHelpers.get_datastore_name,
            MANAGEMENT_NETWORK_DNS_SERVER: TestHelpers.get_mgmt_vm_dns_server,
            MANAGEMENT_NETWORK_GATEWAY: TestHelpers.get_mgmt_vm_gateway,
            MANAGEMENT_NETWORK_IP: TestHelpers.get_mgmt_vm_ip,
            MANAGEMENT_NETWORK_NETMASK: TestHelpers.get_mgmt_vm_netmask,
            MANAGEMENT_PORTGROUP: TestHelpers.get_mgmt_port_group
          },
          networks: [TestHelpers.get_mgmt_port_group],
          dataStores: [EsxCloud::TestHelpers.get_datastore_name]
      }
    end

    def create_esxhypervisor_service
      document_links = setup_esx_hypervisor_entities(1, 1, 0)
      document_links[0]
    end

    def setup_esx_hypervisor_entities(total_hosts = 1, mgmt_hosts = 1, hosts_tagged_mgmt_and_cloud = 0)
      document_links = []

      (0..total_hosts).each{ |num|
        usage_tags = (num < mgmt_hosts) ? ['MGMT'] : ['CLOUD']
        usage_tags.push('CLOUD') if num < hosts_tagged_mgmt_and_cloud

        esx_entity = create_esxhypervisor_entity(usage_tags)

        document_links << create_service(
          EsxCloud::DeployerClient::ESXHYPERVISOR_SERVICE_BASE_PATH,
          esx_entity)
      }

      document_links
    end

    def create_deployment_entity
      {
          imageDataStoreName: TestHelpers.get_datastore_name,
          imageDataStoreUsedForVMs: true
      }
    end

    def create_deployment_service
      deployment_entity = create_deployment_entity

      document_link = create_service(
          EsxCloud::DeployerClient::DEPLOYMENT_SERVICE_BASE_PATH,
          deployment_entity)

      document_link
    end

    def delete_all_created_services
      @created_services.map! { |link| delete_service(link) }
    end
  end
end
