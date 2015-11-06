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

describe EsxCloud::Tenant do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "delegates create to API client" do
    spec = EsxCloud::TenantCreateSpec.new("name")
    spec_hash = { :name => "name", :securityGroups => nil }

    expect(@client).to receive(:create_tenant).with(spec_hash)

    EsxCloud::Tenant.create(spec)
  end

  it "delegates delete to API client" do
    expect(@client).to receive(:delete_tenant).with("foo")
    EsxCloud::Tenant.delete("foo")
  end

  it "delegates find_by_id to API client" do
    expect(@client).to receive(:find_tenant_by_id).with("foo")
    EsxCloud::Tenant.find_by_id("foo")
  end

  it "delegates find_by_name to API client" do
    expect(@client).to receive(:find_tenants_by_name).with("foo")
    EsxCloud::Tenant.find_by_name("foo")
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "t_name"
    }
    from_hash = EsxCloud::Tenant.create_from_hash(hash)
    from_json = EsxCloud::Tenant.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |tenant|
      tenant.id.should == "foo"
      tenant.name.should == "t_name"
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::Tenant.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "can create resource tickets" do
    ticket_options = {:name => "t_name", :limits => []}
    ticket_spec = double(EsxCloud::ResourceTicketCreateSpec)

    EsxCloud::ResourceTicketCreateSpec.stub(:new).with("t_name", []).and_return(ticket_spec)
    expect(EsxCloud::ResourceTicket).to receive(:create).with("foo", ticket_spec)

    EsxCloud::Tenant.new("foo", "name").create_resource_ticket(ticket_options)
  end

  it "can create projects" do
    project_options = {:name => "p_name", :resource_ticket_name => "dev", :limits => [], :security_groups => nil}
    project_spec = double(EsxCloud::ProjectCreateSpec)

    EsxCloud::ProjectCreateSpec.stub(:new).with("p_name", "dev", [], nil).and_return(project_spec)
    expect(EsxCloud::Project).to receive(:create).with("foo", project_spec)

    EsxCloud::Tenant.new("foo", "name").create_project(project_options)
  end

  it "can be deleted" do
    expect(@client).to receive(:delete_tenant).with("foo")

    EsxCloud::Tenant.new("foo", "name").delete
  end

  it "can update security groups" do
    expect(@client).to receive(:set_project_security_groups).with("tenant_id", {items: ["sg1", "sg2"]})

    EsxCloud::Tenant.new("foo", "name").set_project_security_groups("tenant_id", {items: ["sg1", "sg2"]})
  end

end
