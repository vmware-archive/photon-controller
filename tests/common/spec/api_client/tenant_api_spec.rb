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

describe EsxCloud::ApiClient do

  before(:each) do
    @http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(@http_client)
  end

  let(:client) {
    EsxCloud::ApiClient.new("localhost:9000")
  }

  it "creates a tenant" do
    tenant = double(EsxCloud::Tenant)

    expect(@http_client).to receive(:post_json)
                            .with("/tenants", "payload")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "tenant-id"))
    expect(@http_client).to receive(:get).with("/tenants/tenant-id").and_return(ok_response("tenant"))
    expect(EsxCloud::Tenant).to receive(:create_from_json).with("tenant").and_return(tenant)

    client.create_tenant("payload").should == tenant
  end

  it "deletes a tenant" do
    expect(@http_client).to receive(:delete)
                            .with("/tenants/foo")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_tenant("foo").should be_true
  end

  it "deletes a tenant by name" do
    tenants = double(EsxCloud::TenantList, :items => [double(EsxCloud::Tenant, :id => "bar")])

    expect(@http_client).to receive(:get).with("/tenants?name=foo")
                            .and_return(ok_response("tenants"))
    expect(EsxCloud::TenantList).to receive(:create_from_json).with("tenants").and_return(tenants)
    expect(@http_client).to receive(:delete)
                            .with("/tenants/bar")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_tenant_by_name("foo").should be_true
  end

  it "fails to delete a tenant by name if it doesn't exist" do
    tenants = double(EsxCloud::TenantList, :items => [])

    expect(@http_client).to receive(:get).with("/tenants?name=foo")
                            .and_return(ok_response("tenants"))
    expect(EsxCloud::TenantList).to receive(:create_from_json).with("tenants").and_return(tenants)

    expect { client.delete_tenant_by_name("foo") }.to raise_error(EsxCloud::NotFound)
  end

  it "finds tenant by id" do
    tenant = double(EsxCloud::Tenant)

    expect(@http_client).to receive(:get).with("/tenants/foo").and_return(ok_response("tenant"))
    expect(EsxCloud::Tenant).to receive(:create_from_json).with("tenant").and_return(tenant)

    client.find_tenant_by_id("foo").should == tenant
  end

  it "finds all tenants" do
    tenants = double(EsxCloud::TenantList)

    expect(@http_client).to receive(:get).with("/tenants").and_return(ok_response("tenants"))
    expect(EsxCloud::TenantList).to receive(:create_from_json).with("tenants").and_return(tenants)

    client.find_all_tenants.should == tenants
  end

  it "finds tenants by name" do
    tenants = double(EsxCloud::TenantList)

    expect(@http_client).to receive(:get).with("/tenants?name=bar")
                            .and_return(ok_response("tenants"))
    expect(EsxCloud::TenantList).to receive(:create_from_json).with("tenants").and_return(tenants)

    client.find_tenants_by_name("bar").should == tenants
  end

  it "gets tenant tasks" do
    tasks = double(EsxCloud::TaskList)

    expect(@http_client).to receive(:get).with("/tenants/foo/tasks").and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

    client.get_tenant_tasks("foo").should == tasks
  end

  it "sets tenant security groups" do
    security_groups = {items: ["adminGroup1", "adminGroup2"]}

    expect(@http_client).to receive(:post_json)
                            .with("/tenants/foo/set_security_groups", security_groups)
                            .and_return(task_done("task1", "entity-id"))

    expect(@http_client).to receive(:get)
                            .with(URL_HOST + "/tasks/task1")
                            .and_return(task_done("task1", "entity-id"))

    client.set_tenant_security_groups("foo", security_groups)
  end

  it "gets tenant security groups" do
    tenant = EsxCloud::Tenant.new("foo", "name", {securityGroups: ["adminGroup1", "adminGroup2"]})

    expect(@http_client).to receive(:get).with("/tenants/foo").and_return(ok_response("tenant"))
    expect(EsxCloud::Tenant).to receive(:create_from_json).with("tenant").and_return(tenant)
    expect(tenant.security_groups).to eq({:securityGroups => ["adminGroup1", "adminGroup2"]})

    client.find_tenant_by_id("foo")
  end
end
