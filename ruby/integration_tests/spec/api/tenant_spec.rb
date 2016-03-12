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

describe "tenant", management: true do

  it "should create one, get it, then delete it" do
    tenant_name = random_name("tenant-")
    tenant = create_tenant(:name => tenant_name)
    tenant.name.should == tenant_name

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 1
    tenants.items[0].name.should == tenant_name

    validate_tenant_tasks(client.get_tenant_tasks(tenant.id))
    validate_tenant_tasks(client.get_tenant_tasks(tenant.id, "COMPLETED"))

    tenant.delete

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 0
  end

  it "should create one with security groups, get it, then delete it" do
    begin
      tenant_name = random_name("tenant-")
      tenant = create_tenant(:name => tenant_name, :security_groups => ["tenant\\adminGroup1", "tenant\\adminGroup2"])
      tenant.name.should == tenant_name
      tenant.security_groups.should include *[{"name"=>"tenant\\adminGroup1", "inherited"=>false},
                                        {"name"=>"tenant\\adminGroup2", "inherited"=>false}]

      tenants = find_tenants_by_name(tenant_name)
      tenants.items.size.should == 1
      tenants.items[0].name.should == tenant_name

      validate_tenant_tasks(client.get_tenant_tasks(tenant.id))
      validate_tenant_tasks(client.get_tenant_tasks(tenant.id, "COMPLETED"))

      ensure
        tenant.delete

      tenants = find_tenants_by_name(tenant_name)
      tenants.items.size.should == 0
    end
  end

  it "should delete with resource ticket" do
    tenant_name = random_name("tenant-")
    tenant = create_tenant(:name => tenant_name)
    tenant.name.should == tenant_name

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 1

    tenant.create_resource_ticket(:name => random_name("rt-"),
                                  :limits => [{:key => "vm", :value => 100.0, :unit => "COUNT"}])

    tenant.delete

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 0
  end

  it "should fail with a 400 when deleting a tenant with a resource ticket and project" do
    tenant_name = random_name("tenant-")
    tenant = create_tenant(:name => tenant_name)
    tenant.name.should == tenant_name

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 1

    resource_ticket = tenant.create_resource_ticket(:name => random_name("rt-"),
                                                    :limits => [{:key => "vm", :value => 100.0, :unit => "COUNT"}])
    project = tenant.create_project(:name => random_name("project-"),
                                    :resource_ticket_name => resource_ticket.name,
                                    :limits => [{:key => "vm", :value => 10.0, :unit => "COUNT"}])

    begin
      tenant.delete
      fail("There should be an error when deleting a tenant with a project.")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
    rescue EsxCloud::CliError => e
      e.output.should match("not empty")
    end

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 1

    project.delete
    tenant.delete
  end

  it "should delete after project is deleted" do
    tenant_name = random_name("tenant-")
    tenant = create_tenant(:name => tenant_name)
    tenant.name.should == tenant_name

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 1

    resource_ticket = tenant.create_resource_ticket(:name => random_name("rt-"),
                                                    :limits => [{:key => "vm", :value => 100.0, :unit => "COUNT"}])
    project = tenant.create_project(:name => random_name("project-"),
                                    :resource_ticket_name => resource_ticket.name,
                                    :limits => [{:key => "vm", :value => 10.0, :unit => "COUNT"}])

    project.delete
    tenant.delete

    tenants = find_tenants_by_name(tenant_name)
    tenants.items.size.should == 0
  end

  it "should complain on invalid name" do
    error_msg = "name : The specified tenant name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was 1foo)"

    begin
      create_tenant(:name => "1foo")
      fail("Creating a tenant with an invalid name should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].message.should include(error_msg)
    rescue EsxCloud::CliError => e
      e.output.should include(error_msg)
    end
  end

  it "should not allow duplicate names" do
    tenant_name = random_name("tenant-")
    tenant = create_tenant(:name => tenant_name)
    error_msg = "Tenant name '#{tenant_name}' already taken"

    begin
      create_tenant(:name => tenant_name)
      fail("Creating a tenant with a duplicate name should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "NameTaken"
      e.errors[0].message.should == error_msg
    rescue EsxCloud::CliError => e
      e.output.should include(error_msg)
    end

    tenant.delete
  end

  it "should update security groups successfully" do
    begin
      tenant_name = random_name("tenant-")
      tenant = create_tenant(:name => tenant_name)

      tenants = find_tenants_by_name(tenant_name)
      tenants.items.size.should == 1
      tenants.items[0].name.should == tenant_name

      validate_tenant_tasks(client.get_tenant_tasks(tenant.id))
      validate_tenant_tasks(client.get_tenant_tasks(tenant.id, "COMPLETED"))

      security_groups = {items: ["tenant\\adminGroup1", "tenant\\adminGroup2"]}

      client.set_tenant_security_groups(tenant.id, security_groups)

      retrieved_tenant = find_tenant_by_id(tenant.id)
      retrieved_tenant.security_groups.should include *[{"name"=>"tenant\\adminGroup1", "inherited"=>false},
                                                  {"name"=>"tenant\\adminGroup2", "inherited"=>false}]
    ensure
      tenant.delete
    end
  end

  def validate_tenant_tasks(task_list)
    tasks = task_list.items
    tasks.size.should == 1
    task = tasks.first
    [task.entity_kind, task.operation].should == ["tenant", "CREATE_TENANT"]
  end

end
