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

describe "authorization", authorization: true, devbox: true do
  let(:api_client) { ApiClientHelper.management }

  it "gets authentication/authorization information" do
    auth_info = api_client.get_auth_info

    expect(auth_info).to_not be_nil
    expect(auth_info.enabled).to_not be_nil
  end

  describe "#authentication", auth_enabled: true do
    let(:api_client) { EsxCloud::ApiClient.new endpoint, nil, nil }
    let(:endpoint) { ApiClientHelper.endpoint }

    context "when not auth token is provided" do
      [
        *EsxCloud::ApiRoutesHelper.auth_routes
      ].each do |route|
        it "allows #{route.action} #{route.uri} [200]" do
          response = http_client_send route.action, route.uri
          expect(response.code).to eq(200)
        end
      end

      EsxCloud::ApiRoutesHelper.all_routes_excluding_auth_routes.each do |route|
        it "dis-allows with error 401 #{route.action} #{route.uri}" do
          response = http_client_send route.action, route.uri
          expect(response.code).to eq 401
          expect(response.body).to include "MissingAuthToken"
        end
      end
    end
  end

  describe "#authorization", auth_enabled: true do
    let(:api_client) { EsxCloud::ApiClient.new endpoint, nil, token }
    let(:endpoint) { ApiClientHelper.endpoint }

    context "when user is admin", auth_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token }

      EsxCloud::ApiRoutesHelper.all_routes.each do |route|
        it "allows '#{route.action}' #{route.uri} [#{route.rc_admin}]" do
          response = http_client_send route.action, route.uri
          expect(response.code).to eq route.rc_admin
        end
      end
    end

    context "when user is newly created admin user", auth_admin2: true do
      let(:token) { @token }

      before(:all) {
        @token = ApiClientHelper.access_token "ADMIN2"

        @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
        @cleaner = EsxCloud::SystemCleaner.new(client)

        @seeder.tenant!
        @seeder.project!

        @deployment_sgs = @seeder.deployment.auth.securityGroups
        client.update_security_groups(@seeder.deployment!.id,
                                      {items: [ENV["PHOTON_ADMIN2_GROUP"]]})
      }

      after(:all) { client.update_security_groups(@seeder.deployment!.id, items: @deployment_sgs) }
      after(:all) {
        @cleaner.delete_tenant(@seeder.tenant) if @seeder.tenant
        @cleaner.delete_flavor(@seeder.vm_flavor) if @seeder.vm_flavor
        @cleaner.delete_flavor(@seeder.ephemeral_disk_flavor) if @seeder.ephemeral_disk_flavor
        @cleaner.delete_flavor(@seeder.persistent_disk_flavor) if @seeder.persistent_disk_flavor
      }


      it "verifies the return codes from APIs" do
        errors = []
        EsxCloud::ApiRoutesHelper.all_routes_using_seeder(@seeder).each do |route|
          collect_error(errors) do
            p "returns #{route.rc_admin2} from '#{route.action}' #{route.uri}"
            response = http_client_send route.action, route.uri

            error_message = "#{route.action} #{route.uri}: expected(#{route.rc_admin2}) actual(#{response.code})"
            expect(response.code).to eq(route.rc_admin2), error_message
          end
        end

        expect(errors).to eq []
      end
    end

    context "when user is tenant admin", auth_tenant_admin: true do
      let(:token) { @token }

      before(:all) {
        @token = ApiClientHelper.access_token "TENANT_ADMIN"

        @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
        @cleaner = EsxCloud::SystemCleaner.new(client)

        @seeder.project!
        client.set_tenant_security_groups(@seeder.tenant!.id, {:items => [ENV["PHOTON_TENANT_ADMIN_GROUP"]]})
      }

      after(:all) {
        @cleaner.delete_tenant(@seeder.tenant) if @seeder.tenant
        @cleaner.delete_flavor(@seeder.vm_flavor) if @seeder.vm_flavor
        @cleaner.delete_flavor(@seeder.ephemeral_disk_flavor) if @seeder.ephemeral_disk_flavor
        @cleaner.delete_flavor(@seeder.persistent_disk_flavor) if @seeder.persistent_disk_flavor
      }

      it "verifies the return codes from APIs" do
        errors = []
        EsxCloud::ApiRoutesHelper.all_routes_using_seeder(@seeder).each do |route|
          collect_error(errors) do
            p "returns #{route.rc_tenant_admin} from '#{route.action}' #{route.uri}"
            response = http_client_send route.action, route.uri

            error_message = "#{route.action} #{route.uri}: expected(#{route.rc_tenant_admin}) actual(#{response.code})"
            expect(response.code).to eq(route.rc_tenant_admin), error_message
          end
        end

        expect(errors).to eq []
      end
    end

    context "when user is project user", auth_project_user: true do
      let(:token) { @token }

      before(:all) {
        @token = ApiClientHelper.access_token "PROJECT_USER"

        @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
        @cleaner = EsxCloud::SystemCleaner.new(client)

        client.set_project_security_groups(@seeder.project!.id,
                                           :items => [ENV["PHOTON_PROJECT_USER_GROUP"]])
      }
      after(:all) {
        @cleaner.delete_tenant(@seeder.tenant) if @seeder.tenant
        @cleaner.delete_flavor(@seeder.vm_flavor) if @seeder.vm_flavor
        @cleaner.delete_flavor(@seeder.ephemeral_disk_flavor) if @seeder.ephemeral_disk_flavor
        @cleaner.delete_flavor(@seeder.persistent_disk_flavor) if @seeder.persistent_disk_flavor
      }

      it "verifes the return codes from APIs" do
        errors = []
        EsxCloud::ApiRoutesHelper.all_routes_using_seeder(@seeder).each do |route|
          collect_error(errors) do
            p "returns #{route.rc_project_user} from '#{route.action}' #{route.uri}"
            response = http_client_send route.action, route.uri

            error_message = "#{route.action} #{route.uri}: expected(#{route.rc_project_user}) actual(#{response.code})"
            expect(response.code).to eq(route.rc_project_user), error_message
          end
        end

        expect(errors).to eq []
      end
    end

    context "when user is guest user", auth_non_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token "NON_ADMIN" }

      EsxCloud::ApiRoutesHelper.all_routes.each do |route|
        it "returns #{route.rc_guest_user} from '#{route.action}' #{route.uri}" do
          response = http_client_send route.action, route.uri
          expect(response.code).to eq(route.rc_guest_user)
        end
      end
    end

    context "when username is added to project security group", auth_project_user: true do
      let(:token) { @token }

      before(:all) {
        @token = ApiClientHelper.access_token "PROJECT_USER"

        @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
        @cleaner = EsxCloud::SystemCleaner.new(client)

        username = ENV["PHOTON_USERNAME_PROJECT_USER"].split("@")[0]
        domain = ENV["PHOTON_USERNAME_PROJECT_USER"].split("@")[1]
        client.set_project_security_groups(@seeder.project!.id,
                                     :items => ["#{domain}\\#{username}"])
      }
      after(:all) {
        @cleaner.delete_tenant(@seeder.tenant) if @seeder.tenant
        @cleaner.delete_flavor(@seeder.vm_flavor) if @seeder.vm_flavor
        @cleaner.delete_flavor(@seeder.ephemeral_disk_flavor) if @seeder.ephemeral_disk_flavor
        @cleaner.delete_flavor(@seeder.persistent_disk_flavor) if @seeder.persistent_disk_flavor
      }

      it "verifes the return codes from APIs" do
        errors = []
        EsxCloud::ApiRoutesHelper.all_routes_using_seeder(@seeder).each do |route|
          collect_error(errors) do
            p "returns #{route.rc_project_user} from '#{route.action}' #{route.uri}"
            response = http_client_send route.action, route.uri

            error_message = "#{route.action} #{route.uri}: expected(#{route.rc_project_user}) actual(#{response.code})"
            expect(response.code).to eq(route.rc_project_user), error_message
          end
        end

        expect(errors).to eq []
      end
    end
  end

  describe "#list projects user has access to", auth_enabled: true do
    let(:api_client) { EsxCloud::ApiClient.new endpoint, nil, token }
    let(:endpoint) { ApiClientHelper.endpoint }

    before(:all) {
      @cleaner = EsxCloud::SystemCleaner.new(client)

      @tenant1 = create_tenant(:name => random_name("tenant-"), :security_groups => [ENV["PHOTON_TENANT_ADMIN_GROUP"]])
      @tenant2 = create_tenant(:name => random_name("tenant-"))

      rt1 = @tenant1.create_resource_ticket(:name => random_name("rt-"), :limits => [create_limit("vm", 10, "COUNT")])
      @project1 = @tenant1.create_project(:name => random_name("project-"),
                                          :resource_ticket_name => rt1.name,
                                          :limits => [{:key => "vm", :value => 1.0, :unit => "COUNT"}])
      @project2 = @tenant1.create_project(:name => random_name("project-"),
                                          :resource_ticket_name => rt1.name,
                                          :limits => [{:key => "vm", :value => 1.0, :unit => "COUNT"}])
      client.set_project_security_groups(@project2.id, :items => [ENV["PHOTON_PROJECT_USER_GROUP"]])

      rt2 = @tenant2.create_resource_ticket(:name => random_name("rt-"), :limits => [create_limit("vm", 10, "COUNT")])
      @project3 = @tenant2.create_project(:name => random_name("project-"),
                                          :resource_ticket_name => rt2.name,
                                          :limits => [{:key => "vm", :value => 1.0, :unit => "COUNT"}])
    }

    after(:all) {
      @cleaner.delete_tenant(@tenant1) if @tenant1
      @cleaner.delete_tenant(@tenant2) if @tenant2
    }

    context "when user is admin", auth_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token }

      it "should retrieve all projects" do
        projects = api_client.find_all_projects(@tenant1.id).items
        projects.size.should == 2

        projects = api_client.find_all_projects(@tenant2.id).items
        projects.size.should == 1
      end
    end

    context "when user is tenant admin", auth_tenant_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token "TENANT_ADMIN" }

      it "should retrieve all projects under the tenant user is an admin of" do
        projects = api_client.find_all_projects(@tenant1.id).items
        projects.size.should == 2
      end

      it "should not retrieve projects under other tenant user is not an admin of" do
        projects = api_client.find_all_projects(@tenant2.id).items
        projects.size.should == 0
      end
    end

    context "when user is project user", auth_project_user: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token "PROJECT_USER" }

      it "should retrieve projects user is an admin of" do
        projects = api_client.find_all_projects(@tenant1.id).items
        projects.size.should == 1
        projects[0].id.should == @project2.id

        projects = api_client.find_projects_by_name(@tenant1.id, @project2.name).items
        projects.size.should == 1
        projects[0].id.should == @project2.id
      end

      it "should not retrieve projects user is not an admin of" do
        projects = api_client.find_projects_by_name(@tenant1.id, @project1.name).items
        projects.size.should == 0
      end
    end

    context "when user is guest user", auth_non_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token "NON_ADMIN" }

      it "should not retrieve any projects" do
        projects = api_client.find_all_projects(@tenant1.id).items
        projects.size.should == 0

        projects = api_client.find_all_projects(@tenant2.id).items
        projects.size.should == 0
      end
    end
  end

  describe "#list tenants user has access to", auth_enabled: true do
    let(:api_client) { EsxCloud::ApiClient.new endpoint, nil, token }
    let(:endpoint) { ApiClientHelper.endpoint }

    before(:all) {
      @cleaner = EsxCloud::SystemCleaner.new(client)

      @tenant1 = create_tenant(:name => random_name("tenant-"), :security_groups => [ENV["PHOTON_TENANT_ADMIN_GROUP"]])
      @tenant2 = create_tenant(:name => random_name("tenant-"))

      rt1 = @tenant1.create_resource_ticket(:name => random_name("rt-"), :limits => [create_limit("vm", 10, "COUNT")])
      @project1 = @tenant1.create_project(:name => random_name("project-"),
                                          :resource_ticket_name => rt1.name,
                                          :limits => [{:key => "vm", :value => 1.0, :unit => "COUNT"}])
      @project2 = @tenant1.create_project(:name => random_name("project-"),
                                          :resource_ticket_name => rt1.name,
                                          :limits => [{:key => "vm", :value => 1.0, :unit => "COUNT"}])
      client.set_project_security_groups(@project2.id, :items => [ENV["PHOTON_PROJECT_USER_GROUP"]])

      rt2 = @tenant2.create_resource_ticket(:name => random_name("rt-"), :limits => [create_limit("vm", 10, "COUNT")])
      @project3 = @tenant2.create_project(:name => random_name("project-"),
                                          :resource_ticket_name => rt2.name,
                                          :limits => [{:key => "vm", :value => 1.0, :unit => "COUNT"}])
    }

    after(:all) {
      @cleaner.delete_tenant(@tenant1) if @tenant1
      @cleaner.delete_tenant(@tenant2) if @tenant2
    }

    context "when user is admin", auth_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token }

      it "should retrieve all tenants" do
        tenants = api_client.find_all_tenants.items
        tenants.size.should == 2

        tenants = api_client.find_tenants_by_name(@tenant1.name).items
        tenants.size.should == 1
      end
    end

    context "when user is tenant admin", auth_tenant_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token "TENANT_ADMIN" }

      it "should retrieve all tenants user is an admin of" do
        tenants = api_client.find_all_tenants.items
        tenants.size.should == 1
        tenants[0].id.should == @tenant1.id

        tenants = api_client.find_tenants_by_name(@tenant1.name).items
        tenants.size.should == 1
        tenants[0].id.should == @tenant1.id
      end

      it "should not retrieve tenants user is not an admin of" do
        tenants = api_client.find_tenants_by_name(@tenant2.name).items
        tenants.size.should == 0
      end
    end

    context "when user is tennat admin and gets added to security group of a project not owned by user",
            auth_tenant_admin: true do
      let(:token) { @token }

      before(:all) {
        @token = ApiClientHelper.access_token "TENANT_ADMIN"

        @project_sgs = @project3.security_groups.map {|sg| sg["name"]}
        client.set_project_security_groups(@project3.id, :items => [ENV["PHOTON_TENANT_ADMIN_GROUP"]])
      }
      after(:all) {
        client.set_project_security_groups(@project3.id, :items => @project_sgs)
      }

      it "should retrieve all tenants user is an admin of plus the tenant who owns the project" do
        tenants = api_client.find_all_tenants.items
        tenants.size.should == 2

        tenants = api_client.find_tenants_by_name(@tenant1.name).items
        tenants.size.should == 1
        tenants[0].id.should == @tenant1.id

        tenants = api_client.find_tenants_by_name(@tenant2.name).items
        tenants.size.should == 1
        tenants[0].id.should == @tenant2.id
      end
    end

    context "when user is project user", auth_project_user: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token "PROJECT_USER" }

      it "should retrieve all tenants who own at least one project user is an admin of" do
        tenants = api_client.find_all_tenants.items
        tenants.size.should == 1
        tenants[0].id.should == @tenant1.id

        tenants = api_client.find_tenants_by_name(@tenant1.name).items
        tenants.size.should == 1
        tenants[0].id.should == @tenant1.id
      end

      it "should not retrieve tenants who do not own any projects user is an admin of" do
        tenants = api_client.find_tenants_by_name(@tenant2.name).items
        tenants.size.should == 0
      end
    end

    context "when user is guest user", auth_non_admin: true do
      let(:token) { @token }

      before(:all) { @token = ApiClientHelper.access_token "NON_ADMIN" }

      it "should not retrieve any tenants" do
        tenants = api_client.find_all_tenants.items
        tenants.size.should == 0

        tenants = api_client.find_tenants_by_name(@tenant1.name).items
        tenants.size.should == 0
      end
    end
  end

  def http_client_send(action, uri)
    response = if action == :upload
      api_client.http_client.send action, uri, __FILE__
    else
      api_client.http_client.send action, uri
    end

    if response.code == 201
      ignoring_all_errors { api_client.poll_response response }
    end
    response
  end

  def collect_error(errors = [])
    begin
      yield
    rescue Exception => err
      errors << err
    end

    errors
  end
end
