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

require_relative "../spec_helper"

describe EsxCloud::GoCliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    cmd = "/path/to/cli target set --nocertcheck localhost:9000"
    expect(EsxCloud::CmdRunner).to receive(:run).with(cmd)
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  }

  it "creates a tenant" do
    tenant_id = double("tenantID")
    tenant = double(EsxCloud::Tenant, :id => tenant_id)
    spec = { name: "t1" }
    expect(client).to receive(:run_cli).with("tenant create 't1'").and_return(tenant_id)
    expect(client).to receive(:find_tenant_by_id).with(tenant_id).and_return(tenant)
    client.create_tenant(spec).should == tenant
  end

  it "finds tenant by id" do
    tenant_id = double("tenantID")
    tenant_hash = { "id"=>"tenantID",
                    "name"=>"tenant1",
                    "securityGroups"=>[{"name"=>"tenant\\a", "inherited"=>false},
                                       {"name"=>"tenant\\b", "inherited"=>false}]
                   }
    tenant = EsxCloud::Tenant.create_from_hash tenant_hash
    result = "tenantID tenant1 tenant\\a:false,tenant\\b:false"
    expect(client).to receive(:run_cli).with("tenant show #{tenant_id}").and_return(result)
    expect(client).to receive(:get_tenant_from_response).with(result).and_return(tenant)
    client.find_tenant_by_id(tenant_id).should == tenant
  end

  it "finds all tenants" do
    tenants = double(EsxCloud::TenantList)
    result ="t1 tenant1
             t2 tenant2"
    expect(client).to receive(:run_cli).with("tenant list").and_return(result)
    expect(client).to receive(:get_tenant_list_from_response).with(result).and_return(tenants)

    client.find_all_tenants.should == tenants
  end

  it "finds tenants by name" do
    tenants = double(EsxCloud::TenantList)
    expect(@api_client).to receive(:find_tenants_by_name).with("foo").and_return(tenants)
    client.find_tenants_by_name("foo").should == tenants
  end

  it "deletes tenant by id" do
    tenant_id = "tenant_id"
    expect(client).to receive(:run_cli).with("tenant delete '#{tenant_id}'")
    client.delete_tenant(tenant_id).should be_true
  end

  it "deletes tenant by name" do
    tenants = double(EsxCloud::TenantList,
                     :items => [double(EsxCloud::Tenant, :name => "t1", :id => "t1id")])
    expect(client).to receive(:find_tenants_by_name).with("t1").and_return(tenants)
    expect(client).to receive(:run_cli).with("tenant delete 't1id'")
    client.delete_tenant_by_name("t1").should be_true
  end

  it "gets tenant tasks" do
    tenant_id = double("bar")
    result = "task1 COMPLETED CREATE_TENANT  1458853080000  1000
              task2 COMPLETED DELETE_TENANT  1458853089000  1000"
    tasks = double(EsxCloud::TaskList)
    expect(client).to receive(:run_cli).with("tenant tasks '#{tenant_id}' -s 'COMPLETED'").and_return(result)
    expect(client).to receive(:get_task_list_from_response).with(result).and_return(tasks)
    client.get_tenant_tasks(tenant_id, "COMPLETED").should == tasks
  end

  it "sets tenant security groups" do
    tenant_id = "t1"
    security_groups = ["adminGroup1", "adminGroup2"]
    payload = {items: security_groups}
    expect(client).to receive(:run_cli).with(
        "tenant set_security_groups '#{tenant_id}' " + "'" + security_groups.join(",")+"'")
    client.set_tenant_security_groups(tenant_id, payload)
  end
end
