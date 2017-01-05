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

  it "creates a resource ticket" do
    ticket = double(EsxCloud::ResourceTicket)

    expect(@http_client).to receive(:post_json)
                            .with("/tenants/foo/resource-tickets", "payload")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "ticket-id"))
    expect(@http_client).to receive(:get).with("/resource-tickets/ticket-id").and_return(ok_response("ticket"))
    expect(EsxCloud::ResourceTicket).to receive(:create_from_json).with("ticket").and_return(ticket)

    client.create_resource_ticket("foo", "payload").should == ticket
  end

  it "finds resource ticket by id" do
    ticket = double(EsxCloud::ResourceTicket)

    expect(@http_client).to receive(:get).with("/resource-tickets/foo").and_return(ok_response("ticket"))
    expect(EsxCloud::ResourceTicket).to receive(:create_from_json).with("ticket").and_return(ticket)

    client.find_resource_ticket_by_id("foo").should == ticket
  end

  it "finds all resource tickets" do
    tickets = double(EsxCloud::ResourceTicketList)

    expect(@http_client).to receive(:get).with("/tenants/foo/resource-tickets").and_return(ok_response("tickets"))
    expect(EsxCloud::ResourceTicketList).to receive(:create_from_json).with("tickets").and_return(tickets)

    client.find_all_resource_tickets("foo").should == tickets
  end

  it "finds resource tickets by name" do
    tickets = double(EsxCloud::ResourceTicketList)

    expect(@http_client).to receive(:get).with("/tenants/foo/resource-tickets?name=bar")
                            .and_return(ok_response("tickets"))
    expect(EsxCloud::ResourceTicketList).to receive(:create_from_json).with("tickets").and_return(tickets)

    client.find_resource_tickets_by_name("foo", "bar").should == tickets
  end

  it "gets resource ticket tasks" do
    tasks = double(EsxCloud::TaskList)

    expect(@http_client).to receive(:get).with("/resource-tickets/foo/tasks").and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

    client.get_resource_ticket_tasks("foo").should == tasks
  end

end
