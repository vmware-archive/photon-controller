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

require "spec_helper"

describe 'management only host', check_deployment: true do

  let(:api_client) { ApiClientHelper.management(:protocol => "http", :port => "9000") }

  it 'should not place a cloud vm on a management only host' do
    begin
      # Get deployment
      deployment = api_client.find_all_api_deployments.items.first
      mgmt_hosts = api_client.get_deployment_hosts(deployment.id).items.select { |h| h.usage_tags == ["MGMT"] }

      expect(mgmt_hosts).to_not be_empty
      host = mgmt_hosts[0]
      puts host.address

      tenant_name = random_name("tenant-")
      tenant = create_tenant(:name => tenant_name)
      resource_ticket_name = random_name("rt-")
      resource_ticket = tenant.create_resource_ticket(:name => resource_ticket_name,
                                                      :limits => create_small_limits)
      project_name = random_name("project-")
      project = tenant.create_project(
          name: project_name,
          resource_ticket_name: resource_ticket_name,
          limits: create_small_limits)

      vm = create_vm(project,
                     affinities: [{id: host.address, kind: "host"}])

      fail("create vm on a management only host should fail")
    rescue EsxCloud::ApiError => e
      expect(e.response_code).to eq(400)
      expect(e.errors[0].code).to eq("InvalidEntity")
    rescue EsxCloud::CliError => e
      expect(e.message).to include("Invalid")
    end
  end
end
