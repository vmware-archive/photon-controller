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

describe "project", management: true do
  describe "#life_cycle" do
    before(:all) do
      @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
      @cleaner = EsxCloud::SystemCleaner.new(client)
    end

    after(:all) do
      @cleaner.delete_tenant(@seeder.tenant)
    end

    it "should create one, get it, and then delete it" do
      begin
        project_name = random_name("project-")
        project = @seeder.tenant!.create_project(
          name: project_name,
          resource_ticket_name: @seeder.resource_ticket!.name,
          limits: [create_limit("vm", 10.0, "COUNT")])

        project.name.should == project_name
        task_list = client.get_project_tasks(project.id, "COMPLETED").items
        task_list.size.should == 1
        task_list[0].state == "COMPLETED"

        ensure
          project.delete unless project.nil?

        begin
          find_project_by_id(project.id)
          fail("Project should be deleted")
        rescue EsxCloud::ApiError => e
          e.response_code.should == 404
        rescue EsxCloud::CliError => e
          e.output.should match("not found")
        end
      end
    end

    it "should create one with security groups, get it, and then delete it" do
      begin
        project_name = random_name("project-")
        project = @seeder.tenant!.create_project(
            name: project_name,
            resource_ticket_name: @seeder.resource_ticket!.name,
            limits: [create_limit("vm", 10.0, "COUNT")],
            security_groups: ["tenant\\adminGroup1", "tenant\\adminGroup2"])

        project.name.should == project_name
        project.security_groups.should include *[{"name"=>"tenant\\adminGroup1", "inherited"=>false},
                                           {"name"=>"tenant\\adminGroup2", "inherited"=>false}]
        task_list = client.get_project_tasks(project.id, "COMPLETED").items
        task_list.size.should == 1
        task_list[0].state == "COMPLETED"

        ensure
          project.delete unless project.nil?

        begin
          find_project_by_id(project.id)
          fail("Project should be deleted")
        rescue EsxCloud::ApiError => e
          e.response_code.should == 404
        rescue EsxCloud::CliError => e
          e.output.should match("not found")
        end
      end
    end

    context "when tenant has security groups" do
      before(:all) do
        tenant_security_groups = {items: ["tenant\\adminGroup2", "tenant\\adminGroup3"]}
        client.set_tenant_security_groups(@seeder.tenant!.id, tenant_security_groups)
      end

      after(:all) do
        client.set_tenant_security_groups(@seeder.tenant!.id, {items: []})
      end

      it "creates one with security groups and import from tenant, get it, and then delete it" do
        begin
          project_name = random_name("project-")

          retrieved_tenant = find_tenant_by_id(@seeder.tenant!.id)
          retrieved_tenant.security_groups.should include *[{"name"=>"tenant\\adminGroup2", "inherited"=>false},
                                                      {"name"=>"tenant\\adminGroup3", "inherited"=>false}]
          project = @seeder.tenant!.create_project(
              name: project_name,
              resource_ticket_name: @seeder.resource_ticket!.name,
              limits: [create_limit("vm", 10.0, "COUNT")],
              security_groups: ["tenant\\adminGroup1", "tenant\\adminGroup2"])

          project.name.should == project_name
          project.security_groups.should include *[{"name"=>"tenant\\adminGroup2", "inherited"=>true},
                                             {"name"=>"tenant\\adminGroup3", "inherited"=>true},
                                             {"name"=>"tenant\\adminGroup1", "inherited"=>false}]
          task_list = client.get_project_tasks(project.id, "COMPLETED").items
          task_list.size.should == 1
          task_list[0].state == "COMPLETED"

          ensure
            project.delete unless project.nil?

          begin
            find_project_by_id(project.id)
            fail("Project should be deleted")
          rescue EsxCloud::ApiError => e
            e.response_code.should == 404
          rescue EsxCloud::CliError => e
            e.output.should match("not found")
          end
        end
      end
    end
  end

  describe "#create" do
    before(:all) do
      @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
      @cleaner = EsxCloud::SystemCleaner.new(client)
    end

    after(:all) do
      @cleaner.delete_tenant(@seeder.tenant)
    end

    it "complains on invalid name" do
      error_msg = "name : The specified project name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was 1foo)"

      begin
        @seeder.tenant!.create_project(name: "1foo",
                                       resource_ticket_name: @seeder.resource_ticket!.name,
                                       limits: [create_limit("vm", 10.0, "COUNT")])
        fail("Creating a project with an invalid name should fail")
      rescue EsxCloud::ApiError => e
        e.response_code.should == 400
        e.errors.size.should == 1
        e.errors[0].message.should == error_msg
      rescue EsxCloud::CliError => e
        e.output.should include(error_msg)
      end
    end

    it "does not allow duplicate project names" do
      project_name = random_name("project-")
      project = @seeder.tenant!.create_project(
        name: project_name,
        resource_ticket_name: @seeder.resource_ticket!.name,
        limits: [create_limit("vm", 10.0, "COUNT")])
      error_msg = "Project name '#{project_name}' already taken"

      begin
        @seeder.tenant!.create_project(
          name: project_name,
          resource_ticket_name: @seeder.resource_ticket!.name,
          limits: [create_limit("vm", 10.0, "COUNT")])
        fail("Creating a project with a duplicate name should fail")
      rescue EsxCloud::ApiError => e
        e.response_code.should == 400
        e.errors.size.should == 1
        e.errors[0].code.should == "NameTaken"
        e.errors[0].message.should == error_msg
      rescue EsxCloud::CliError => e
        e.output.should match(error_msg)
      ensure
        project.delete unless project.nil?
      end
    end
  end

  describe "#delete" do
    before(:all) do
      @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
      @cleaner = EsxCloud::SystemCleaner.new(client)
    end

    after(:all) do
      @cleaner.delete_tenant(@seeder.tenant)
    end

    subject do
      @seeder.tenant!.create_project(
          name: random_name("project-"),
          resource_ticket_name: @seeder.resource_ticket!.name,
          limits: [create_limit("vm", 10.0, "COUNT")])
    end

    context "when project has VMs" do
      before(:each) do
        create_vm(subject)
      end

      it "fails to delete" do
        error_msg =
            "Project '#{subject.id}' VM list is non-empty"

        begin
          subject.delete
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "ContainerNotEmpty"
          e.errors[0].message.should match(error_msg)
        rescue EsxCloud::CliError => e
          e.output.should match(error_msg)
        end
      end
    end

    context "when project has disks" do
      before(:each) do
        subject.create_disk(
            name: random_name("disk-"),
            kind: "persistent-disk",
            flavor: @seeder.persistent_disk_flavor!.name,
            capacity_gb: 2,
            boot_disk: false)
      end

      it "fails to delete" do
        error_msg =
            "Project '#{subject.id}' persistent disk list is non-empty"

        begin
          subject.delete
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "ContainerNotEmpty"
          e.errors[0].message.should match(error_msg)
        rescue EsxCloud::CliError => e
          e.output.should match(error_msg)
        end
      end
    end

    context "when project has virtual subnets" do
      before(:each) do
        pending("SDN not enabled") unless @seeder.deployment.network_configuration.virtual_network_enabled

        spec = EsxCloud::VirtualNetworkCreateSpec.new(random_name("network-"), "TMP subnet", "ROUTED", 128, 16)
        EsxCloud::VirtualNetwork.create(subject.id, spec)
      end

      it "fails to delete" do
        error_msg = "Project '#{subject.id}' subnet list is non-empty"

        begin
          subject.delete
        rescue EsxCloud::ApiError => e
          e.response_code.should == 400
          e.errors.size.should == 1
          e.errors[0].code.should == "ContainerNotEmpty"
          e.errors[0].message.should match(error_msg)
        rescue EsxCloud::CliError => e
          e.output.should match(error_msg)
        end
      end
    end
  end

  describe "#update security group" do
    before(:all) do
      @seeder = EsxCloud::SystemSeeder.new([create_limit("vm", 100.0, "COUNT")])
      @cleaner = EsxCloud::SystemCleaner.new(client)
    end

    after(:all) do
      @cleaner.delete_tenant(@seeder.tenant)
    end

    it "should update security groups successfully" do
      begin
        project_name = random_name("project-")
        project = @seeder.tenant!.create_project(
            name: project_name,
            resource_ticket_name: @seeder.resource_ticket!.name,
            limits: [create_limit("vm", 10.0, "COUNT")])

        project.name.should == project_name
        task_list = client.get_project_tasks(project.id, "COMPLETED").items
        task_list.size.should == 1
        task_list[0].state == "COMPLETED"

        security_groups = {items: ["tenant\\adminGroup1", "tenant\\adminGroup2"]}
        client.set_project_security_groups(project.id, security_groups)

        retrieved_project = find_project_by_id(project.id)
        retrieved_project.security_groups.should include *[{"name"=>"tenant\\adminGroup1", "inherited"=>false},
                                                     {"name"=>"tenant\\adminGroup2", "inherited"=>false}]
      ensure
        project.delete unless project.nil?
      end
    end

    it "should push the security group changes from tenant to project" do
      begin
        project_name = random_name("project-")
        project = @seeder.tenant!.create_project(
            name: project_name,
            resource_ticket_name: @seeder.resource_ticket!.name,
            limits: [create_limit("vm", 10.0, "COUNT")])

        project.name.should == project_name
        task_list = client.get_project_tasks(project.id, "COMPLETED").items
        task_list.size.should == 1
        task_list[0].state == "COMPLETED"

        security_groups = {items: ["tenant\\adminGroup1", "tenant\\adminGroup2"]}
        client.set_project_security_groups(project.id, security_groups)

        retrieved_project = find_project_by_id(project.id)
        retrieved_project.security_groups.should include *[{"name"=>"tenant\\adminGroup1", "inherited"=>false},
                                                     {"name"=>"tenant\\adminGroup2", "inherited"=>false}]

        tenant_security_groups = {items: ["tenant\\adminGroup2", "tenant\\adminGroup3"]}

        client.set_tenant_security_groups(@seeder.tenant!.id, tenant_security_groups)

        retrieved_tenant = find_tenant_by_id(@seeder.tenant!.id)
        retrieved_tenant.security_groups.should include *[{"name"=>"tenant\\adminGroup2", "inherited"=>false},
                                                    {"name"=>"tenant\\adminGroup3", "inherited"=>false}]

        retrieved_project = find_project_by_id(project.id)
        retrieved_project.security_groups.should include *[{"name"=>"tenant\\adminGroup2", "inherited"=>true},
                                                     {"name"=>"tenant\\adminGroup3", "inherited"=>true},
                                                     {"name"=>"tenant\\adminGroup1", "inherited"=>false}]
      ensure
        project.delete unless project.nil?
      end
    end
  end
end
