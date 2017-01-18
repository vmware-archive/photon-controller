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
require_relative "../../../lib/dcp/cloud_store/deployment_factory"

describe "deployment", management: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
  end

  let(:deployment) do
    @seeder.deployment
  end

  describe "#set_security_groups", disable_for_cli_test: true do
    context "when auth is off", auth_disabled: true do
      it "fails to update security groups" do
        begin
          deployment_id = deployment.id
          security_groups = ["esxcloud\\adminGroup1", "esxcloud\\adminGroup2"]
          security_groups_in_hash = {items: security_groups}

          client.update_security_groups(deployment_id, security_groups_in_hash)
          fail "setting security groups should have failed"
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq "InvalidAuthConfig"
          expect(e.errors.first.message).to include("Auth is not enabled, and security groups cannot be set.")
        end
      end
    end

    context "when auth is on", auth_enabled: true do
      before(:each) do
        @deployment_sgs = deployment.auth.securityGroups
        @tenant = @seeder.tenant!
        @project = @tenant.create_project(
            name: random_name("project-"),
            resource_ticket_name: @seeder.resource_ticket!.name,
            limits: [create_limit("vm", 10.0, "COUNT")])
      end

      after(:each) { client.update_security_groups(deployment.id, items: @deployment_sgs) }
      after(:each) do
        @project.delete
        @seeder.tenant.delete
      end

      it "should successfully update security groups and push them to tenants and projects" do
        sg_list = deployment.auth.securityGroups
        sg_list << "esxcloud\\adminGroup2"
        sg_list << "esxcloud\\adminGroup3"

        deployment_id = deployment.id;

        security_groups = {items: sg_list}
        client.update_security_groups(deployment_id, security_groups)

        deployment = client.find_deployment_by_id(deployment_id)
        expect(deployment.auth.securityGroups).to eq(sg_list)

        # validate inheritance for tenant and project
        expected_inherited_list = sg_list.map do |sg|
          {"name"=> sg, "inherited"=>true}
        end
        tenant = client.find_tenant_by_id(@tenant.id)
        tenant.security_groups.should =~ expected_inherited_list

        project = client.find_project_by_id(@project.id)
        project.security_groups.should =~ expected_inherited_list
      end
    end
  end

  describe "#update_image_datastores", go_cli: true do
    before(:each) do
      @existing_image_datastores = deployment.image_datastores
    end

    it "fails to update image datastores" do
      begin
        begin
          client.update_image_datastores(deployment.id, ["new_image_datastore"])
        rescue EsxCloud::CliError => e
          expect(e.message).to include("New image datastore list [new_image_datastore] is not a super set of existing list")
        end
      end
    end

    it "should successfully update image datastores" do
        new_image_datastore_list = [@existing_image_datastores, "new_image_datastores"].flatten
        client.update_image_datastores(deployment.id, new_image_datastore_list)

        updated_image_datastores = client.find_deployment_by_id(deployment.id).image_datastores
        expect(updated_image_datastores).to match_array(new_image_datastore_list)
    end
  end
end
