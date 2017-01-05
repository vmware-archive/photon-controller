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

describe "resource ticket", management: true do
  let(:vm_flavor) { EsxCloud::SystemSeeder.instance.vm_flavor! }

  before(:all) do
    @tenant = create_random_tenant
    @cleaner = EsxCloud::SystemCleaner.new(client)
    @project = nil
  end

  after(:all) do
    @cleaner.delete_tenant(@tenant)
  end

  after(:each) do
    if @vm
      @vm.delete
    end
    if @project
      @project.delete
    end
  end

  it "should create one" do
    resource_ticket_name = random_name("rt-")
    resource_ticket = @tenant.create_resource_ticket(:name => resource_ticket_name, :limits => create_small_limits)
    resource_ticket.name.should == resource_ticket_name

    validate_resource_ticket_tasks(client.get_resource_ticket_tasks(resource_ticket.id))
    validate_resource_ticket_tasks(client.get_resource_ticket_tasks(resource_ticket.id, "COMPLETED"))
  end

  it "should complain on invalid name" do
    error_msg = "name : The specified resource ticket name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was 1foo)"

    begin
      @tenant.create_resource_ticket(:name => "1foo", :limits => create_small_limits)
      fail("Creating a resource ticket with an invalid name should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].message.should include(error_msg)
    rescue EsxCloud::CliError => e
      e.output.should include(error_msg)
    end
  end

  it "should not allow duplicate names" do
    ticket_name = random_name("rt-")
    resource_ticket = @tenant.create_resource_ticket(:name => ticket_name, :limits => create_small_limits)

    begin
      @tenant.create_resource_ticket(:name => resource_ticket.name, :limits => create_small_limits)
      fail("Creating a resource ticket with a duplicate name should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "NameTaken"
    rescue EsxCloud::CliError => e
      e.output.should match("NameTaken")
    end
  end

  it "should fail on vm.memory quota exceeded" do
    cost = get_vm_flavor_cost_key(vm_flavor.name, "vm.memory")
    limit_value = cost[:value] * 0.95
    limits = [create_limit("vm.memory", limit_value, cost[:unit])]

    resource_ticket = @tenant.create_resource_ticket(:name => random_name("rt-"), :limits => limits)
    @project = @tenant.create_project(:name => random_name("project-"), :resource_ticket_name => resource_ticket.name,
                                     :limits => [create_subdivide_limit(100.0)])

    begin
      create_vm(@project)
      fail("Creating a vm with more memory than the limit allows should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "QuotaError"
    rescue EsxCloud::CliError => e
      e.output.should match("QuotaError")
    end
  end

  it "should fail on vm quota exceeded" do
    limits = [create_limit("vm", 1.0, "COUNT")]

    resource_ticket = @tenant.create_resource_ticket(:name => random_name("rt-"), :limits => limits)
    @project = @tenant.create_project(:name => random_name("project-"), :resource_ticket_name => resource_ticket.name,
                                      :limits => [create_subdivide_limit(100.0)])

    vm = create_vm(@project)

    begin
      create_vm(@project)

      fail("Creating more VMs than the limit allows should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "QuotaError"
    rescue EsxCloud::CliError => e
      e.output.should match("QuotaError")
    ensure
      vm.delete
    end
  end

  xit "should fail on virtual network quota exceeded" do
    limits = [create_limit("sdn.size", 8, "COUNT")]
    spec = EsxCloud::VirtualNetworkCreateSpec.new(random_name("network-"), "virtual network", "ROUTED", 8, 2)

    resource_ticket = @tenant.create_resource_ticket(:name => random_name("rt-"), :limits => limits)
    @project = @tenant.create_project(:name => random_name("project-"), :resource_ticket_name => resource_ticket.name,
                                      :limits => [create_subdivide_limit(100.0)])

    network = create_network(spec)

    begin
      create_network(spec)

      fail("Creating virtual networks with more than allowed private IP count limit should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "QuotaError"
    rescue EsxCloud::CliError => e
      e.output.should match("QuotaError")
    ensure
      network.delete
    end
  end

  it "should fail on vm.cpu quota exceeded" do
    cost = get_vm_flavor_cost_key(vm_flavor.name, "vm.cpu")
    limit_value = cost[:value] * 0.9
    limits = [create_limit("vm.cpu", limit_value, cost[:unit])]

    resource_ticket = @tenant.create_resource_ticket(:name => random_name("rt-"), :limits => limits)
    @project = @tenant.create_project(:name => random_name("project-"), :resource_ticket_name => resource_ticket.name,
                                     :limits => [create_subdivide_limit(100.0)])

    begin
      create_vm(@project)

      fail("Creating a VM with more cpu than the limit allows should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "QuotaError"
    rescue EsxCloud::CliError => e
      e.output.should match("QuotaError")
    end
  end

  it "should fail on vm.cost quota exceeded" do
    cost = get_vm_flavor_cost_key(vm_flavor.name, "vm.cost")
    limit_value = cost[:value] * 0.9
    limits = [create_limit("vm.cpu", limit_value, cost[:unit])]

    resource_ticket = @tenant.create_resource_ticket(:name => random_name("rt-"), :limits => limits)
    @project = @tenant.create_project(:name => random_name("project-"), :resource_ticket_name => resource_ticket.name,
                                      :limits => [create_subdivide_limit(100.0)])

    begin
      create_vm(@project)

      fail("Creating a VM with more cpu than the limit allows should fail")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 400
      e.errors.size.should == 1
      e.errors[0].code.should == "QuotaError"
    rescue EsxCloud::CliError => e
      e.output.should match("QuotaError")
    end
  end

  it "should calculate usage correctly" do
    resource_ticket = @tenant.create_resource_ticket(:name => random_name("rt-"), :limits => create_small_limits)
    @project = @tenant.create_project(:name => random_name("project-"), :resource_ticket_name => resource_ticket.name,
                                      :limits => [create_subdivide_limit(50.0)])
    resource_ticket = find_resource_ticket_by_id(resource_ticket.id)
    usages = resource_ticket.usage
    limits = resource_ticket.limits

    # Check resource_ticket usages shows that 50% has been used by @project.
    limits.each do |qli|
      usage = usages.select { |usage_qli| usage_qli.key == qli.key }
      usage.length.should == 1
      usage[0].unit.should == qli.unit
      usage[0].value.should == 0.5 * qli.value
    end

    # Create a vm on @project and check that @project usage reflects what the flavor takes.
    @vm = create_vm(@project)

    @project = find_project_by_id(@project.id)
    usages = @project.resource_ticket.usage

    costs = get_vm_flavor(vm_flavor.name).cost
    memory_cost = costs.select { |cost| cost.key == "vm.memory" }
    memory_cost.length.should == 1
    memory_cost = memory_cost[0].to_hash
    memory_value = memory_cost[:value]
    memory_unit = memory_cost[:unit]

    mem_usage = usages.select { |usage_qli| usage_qli.key == "vm.memory" }[0]

    _, memory_value, mem_usage.value = convert_units(memory_unit, memory_value, mem_usage.unit, mem_usage.value)

    mem_usage.value.should == memory_value
    vm_usage = (usages.select { |usage_qli| usage_qli.key == "vm" })[0]
    vm_usage.value.should == 1.0

    cpu_usage = (usages.select { |usage_qli| usage_qli.key == "vm.cpu" })[0]
    cpu_cost = costs.select { |cost| cost.key == "vm.cpu" }[0]
    cpu_usage.value.should == cpu_cost.value

    cost_usage = (usages.select { |usage_qli| usage_qli.key == "vm.cost" })[0]
    vm_cost = costs.select { |cost| cost.key == "vm.cost" }[0]
    cost_usage.value.should == vm_cost.value
  end

  private

  def validate_resource_ticket_tasks(task_list)
    tasks = task_list.items
    tasks.size.should == 1
    task = tasks.first
    [task.entity_kind, task.operation].should == ["resource-ticket", "CREATE_RESOURCE_TICKET"]
  end
end
