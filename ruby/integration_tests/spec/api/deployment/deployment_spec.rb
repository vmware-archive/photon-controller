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

  describe "#create" do
    shared_examples "failed validation" do |error_msgs, error_code|
      it "fails payload validation" do
        begin
          client.create_api_deployment(deployment_create_spec.to_hash)
          fail("payload validation should fail")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq(error_code)
        rescue EsxCloud::CliError => e
          error_msgs.each { |error_msg| expect(e.output).to include(error_msg) }
        end
      end
    end

    let(:spec) do
      EsxCloud::DeploymentCreateSpec.new(
        ["image_datastore"],
        EsxCloud::AuthConfigurationSpec.new(false),
        EsxCloud::NetworkConfigurationSpec.new(false),
        EsxCloud::StatsInfo.new(false),
        "0.0.0.1",
        "0.0.0.2",
        true)
    end

    context "when auth config is invalid in deploy request" do
      context "when auth is enabled but tenant/username/password/securityGroups are not specified" do
        it_behaves_like "failed validation",
                        ["OAuth tenant cannot be nil when auth is enabled."],
                        "InvalidAuthConfig" do
          let(:deployment_create_spec) do
            EsxCloud::DeploymentCreateSpec.new(
              ["image_datastore"],
              EsxCloud::AuthConfigurationSpec.new(true),
              EsxCloud::NetworkConfigurationSpec.new(false),
              EsxCloud::StatsInfo.new(false),
              "0.0.0.1",
              "0.0.0.2",
              true)
          end
        end
      end

      context "when auth is not enabled but tenant/password/securityGroups are specified" do
        it_behaves_like "failed validation",
                        ["password must be null (was p)",
                         "securityGroups must be null",
                         "tenant must be null (was t)"],
                        "InvalidAuthConfig" do
          let(:deployment_create_spec) do
            EsxCloud::DeploymentCreateSpec.new(
              ["image_datastore"],
              EsxCloud::AuthConfigurationSpec.new(false, 't', 'p', ['t\\securityGroup1']),
              EsxCloud::NetworkConfigurationSpec.new(false),
              EsxCloud::StatsInfo.new(false),
              "0.0.0.1",
              "0.0.0.2",
              true)
          end
        end
      end
    end

    context "when image datastore is not specified" do
      it_behaves_like "failed validation",
                      ["Image datastore names cannot be nil"],
                      "InvalidEntity" do
        let(:deployment_create_spec) do
          spec.image_datastores = nil
          spec
        end
      end
    end
  end

  describe "#delete" do
    context "when deployment id is not valid" do
      it "fails to delete the deployment" do
        invalid_deployment_id = "invalid-deployment"
        error_msg = "Deployment ##{invalid_deployment_id} not found"
        begin
          client.delete_api_deployment(invalid_deployment_id)
          fail("delete deployment should fail when the deployment is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("DeploymentNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end

    context "when deployment state is 'NOT_DEPLOYED'" do
      it "fails to delete the deployment" do
        error_msg = "Invalid operation DELETE_DEPLOYMENT for deployment/#{deployment.id} in state READY"
        begin
          client.delete_api_deployment(deployment.id)
          fail("delete deployment should fail when the deployment is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("StateError")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#deploy" do
    context "when deployment state is 'READY'" do
      it "fails to deploy" do
        expect(deployment.state).to eq "READY"

        error_msg = "Invalid operation PERFORM_DEPLOYMENT for deployment/#{deployment.id} in state READY"
        begin
          client.deploy_deployment(deployment.id)
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 400
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("StateError")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#destroy" do
    context "when deployment id is not valid" do
      it "fails to destroy the deployment" do
        invalid_deployment_id = "invalid-deployment"
        error_msg = "Deployment ##{invalid_deployment_id} not found"
        begin
          client.destroy_deployment(invalid_deployment_id)
          fail("destroy deployment should fail when the deployment is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("DeploymentNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
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
        expect(deployment.auth.securityGroups).to eq([])

        deployment_id = deployment.id;

        security_groups = {items: ["esxcloud\\adminGroup2", "esxcloud\\adminGroup3"]}
        client.update_security_groups(deployment_id, security_groups)

        deployment = client.find_deployment_by_id(deployment_id)
        expect(deployment.auth.securityGroups).to eq(["esxcloud\\adminGroup2", "esxcloud\\adminGroup3"])

        tenant = client.find_tenant_by_id(@tenant.id)
        tenant.security_groups.should =~ [{"name"=>"esxcloud\\adminGroup2", "inherited"=>true},
                                          {"name"=>"esxcloud\\adminGroup3", "inherited"=>true}]

        project = client.find_project_by_id(@project.id)
        project.security_groups.should =~ [{"name"=>"esxcloud\\adminGroup2", "inherited"=>true},
                                           {"name"=>"esxcloud\\adminGroup3", "inherited"=>true}]
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
      begin
        new_image_datastore_list = [@existing_image_datastores, "new_image_datastores"].flatten
        client.update_image_datastores(deployment.id, new_image_datastore_list)

        updated_image_datastores = client.find_deployment_by_id(deployment.id).image_datastores
        expect(updated_image_datastores).to match_array(new_image_datastore_list)
      ensure
        factory = EsxCloud::Dcp::CloudStore::DeploymentFactory.new
        instance_link = factory.instance_link

        payload = {
            imageDataStoreNames: @existing_image_datastores
        }
        EsxCloud::Dcp::CloudStore::CloudStoreClient.instance.patch instance_link, payload
      end
    end
  end
end
