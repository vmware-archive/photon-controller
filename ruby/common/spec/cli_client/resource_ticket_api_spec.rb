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

describe EsxCloud::CliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    EsxCloud::CliClient.new("/path/to/cli", "localhost:9000")
  }

  it "creates a resource ticket" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    ticket = double(EsxCloud::ResourceTicket)
    tickets = double(EsxCloud::ProjectList, :items => [ticket])

    spec = {
      :name => "rt",
      :limits => [
        {:key => "a", :value => "b", :unit => "c"},
        {:key => "d", :value => "e", :unit => "f"}
      ]
    }

    expect(@api_client).to receive(:find_tenant_by_id).with("t1").and_return(tenant)

    expect(client).to receive(:run_cli).with("resource-ticket create -t 't1' -n 'rt' -l 'a b c, d e f'")
    expect(client).to receive(:find_resource_tickets_by_name).and_return(tickets)

    client.create_resource_ticket("t1", spec).should == ticket
  end

  it "finds all tickets" do
    tickets = double(EsxCloud::ResourceTicketList)
    expect(@api_client).to receive(:find_all_resource_tickets).with("foo").and_return(tickets)
    client.find_all_resource_tickets("foo").should == tickets
  end

  it "finds tickets by name" do
    tickets = double(EsxCloud::ResourceTicketList)
    expect(@api_client).to receive(:find_resource_tickets_by_name).with("foo", "bar").and_return(tickets)
    client.find_resource_tickets_by_name("foo", "bar").should == tickets
  end

  it "finds ticket by id" do
    ticket = double(EsxCloud::ResourceTicket)
    expect(@api_client).to receive(:find_resource_ticket_by_id).with("foo").and_return(ticket)
    client.find_resource_ticket_by_id("foo").should == ticket
  end

  it "gets resource ticket tasks" do
    tasks = double(EsxCloud::TaskList)
    expect(@api_client).to receive(:get_resource_ticket_tasks).with("foo", "a").and_return(tasks)
    client.get_resource_ticket_tasks("foo", "a").should == tasks
  end

end
