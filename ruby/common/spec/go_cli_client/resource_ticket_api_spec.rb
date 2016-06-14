# Copyright 2016 VMware, Inc. All Rights Reserved.
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

  it "creates a resource ticket" do
    tenant_id = double("t1")
    tenant_name = double("t1_name")
    tenant = double(EsxCloud::Tenant, :name => tenant_name)
    ticket_id ="foo"
    ticket = double(EsxCloud::ResourceTicket, :id => ticket_id)

    spec = {
        :name => "rt",
        :limits => [
            {:key => "a", :value => "b", :unit => "c"},
            {:key => "d", :value => "e", :unit => "f"}
        ]
    }

    expect(client).to receive(:find_tenant_by_id).with(tenant_id).and_return(tenant)

    expect(client).to receive(:run_cli).with("resource-ticket create -t '#{tenant_name}' -n 'rt' -l 'a b c, d e f'").and_return(ticket_id)
    expect(client).to receive(:find_resource_ticket_by_id).with(ticket_id).and_return(ticket)

    client.create_resource_ticket(tenant_id, spec).should == ticket
  end

  it "finds all tickets" do
    tenant_id = double("t1")
    tenant_name = double("t1_name")
    tenant = double(EsxCloud::Tenant, :name => tenant_name)
    tickets = double(EsxCloud::ResourceTicketList)
    result = "id1	rt1	a:b:c,d:e:f
              id2	rt2	a:b:c,d:e:f"

    expect(client).to receive(:find_tenant_by_id).with(tenant_id).and_return(tenant)
    expect(client).to receive(:run_cli).with("resource-ticket list -t '#{tenant_name}'").and_return(result)
    expect(client).to receive(:get_resource_ticket_list_from_response).with(result).and_return(tickets)

    client.find_all_resource_tickets(tenant_id).should == tickets
  end

  it "finds all tickets by name" do
    tickets = double(EsxCloud::ResourceTicketList)
    expect(@api_client).to receive(:find_resource_tickets_by_name).with("tenant", "foo").and_return(tickets)
    expect(client.find_resource_tickets_by_name("tenant", "foo")).to be(tickets)
  end

  it "finds ticket by name" do
    tenant_id = double("t1")
    tenant_name = double("t1_name")
    tenant = double(EsxCloud::Tenant, :name => tenant_name)
    ticket = double(EsxCloud::ResourceTicket)
    result = "id1	rt1	a:b:c,d:e:f"

    expect(client).to receive(:find_tenant_by_id).with(tenant_id).and_return(tenant)
    expect(client).to receive(:run_cli).with("resource-ticket show 'rt1' -t '#{tenant_name}'").and_return(result)
    expect(client).to receive(:get_resource_ticket_from_response).with(result,tenant_id).and_return(ticket)
    client.find_resource_ticket_by_name(tenant_id, "rt1").should == ticket
  end

  it "finds ticket by id" do
    ticket = double(EsxCloud::ResourceTicket)
    expect(@api_client).to receive(:find_resource_ticket_by_id).with("foo").and_return(ticket)
    client.find_resource_ticket_by_id("foo").should == ticket
  end
end
