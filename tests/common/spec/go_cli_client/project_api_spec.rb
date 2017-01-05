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

  it "creates a project" do
    project_id = double("aaa")
    project = double(EsxCloud::Project, :id => project_id)
    tenant = double(EsxCloud::Tenant, :name => "t1")

    spec = {
        :name => "p1",
        :resourceTicket => {
            :name => "bar",
            :limits => [
                {:key => "a", :value => "b", :unit => "c"},
                {:key => "d", :value => "e", :unit => "f"}
            ]
        }
    }

    expect(client).to receive(:find_tenant_by_id).with("foo").and_return(tenant)

    expect(client).to receive(:run_cli).with("project create -t 't1' -n 'p1' -r 'bar' -l 'a b c, d e f'").and_return(project_id)
    expect(client).to receive(:find_project_by_id).with(project_id).and_return(project)

    client.create_project("foo", spec).should == project
    client.project_to_tenant[project_id].should == tenant
  end

  it "finds project by id" do
    project_id = double("project1-id")
    project_hash = { "id" => project_id,
                     "name" => "project1",
                     "resourceTicket" => { "tenantTicketId" => "ticketID",
                                            "tenantTicketName" => "ticketName",
                                            "limits" => [{"key" => "a", "value" => "b", "unit" => "c"}],
                                            "usage" => [{"key" => "d", "value" => "e", "unit" => "f"}]
                     },
                     "securityGroups"=> [{"name"=>"a\\b", "inherited"=>false},
                                         {"name"=>"c\\d", "inherited"=>false}]
                   }
    project = EsxCloud::Project.create_from_hash(project_hash)
    result = "project1-id project1  ticketID  ticketName  a:b:c d:e:f a\\b:false,c\\d:false"
    expect(client).to receive(:run_cli).with("project show #{project_id}").and_return(result)
    expect(client).to receive(:get_project_from_response).with(result).and_return(project)
    client.find_project_by_id(project_id).should == project
  end

  it "deletes project" do
    project_id = double("aaa")

    expect(client).to receive(:run_cli).with("project delete #{project_id}")
    client.delete_project(project_id).should be_true
  end

  it "finds all projects" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    projects = double(EsxCloud::ProjectList)
    result ="p1 project1  a:b:c d:e:f
             p2 project2  a:b:c d:e:f"

    expect(client).to receive(:find_tenant_by_id).with("tenantID").and_return(tenant)
    expect(client).to receive(:run_cli).with("project list -t 't1'").and_return(result)
    expect(client).to receive(:get_project_list_from_response).with(result).and_return(projects)

    client.find_all_projects("tenantID").should == projects
  end

  it "sets project security groups" do
    project_id = double("aaa")
    security_groups = {items: ["adminGroup1", "adminGroup2"]}

    expect(client).to receive(:run_cli)
                      .with("project set_security_groups '#{project_id}' '#{security_groups[:items].join(",")}'")
    client.set_project_security_groups(project_id, security_groups)
  end

  it "gets project tasks" do
    project_id = double("bar")
    result = "task1 COMPLETED CREATE_DISK  1458853080000  1000
              task2 COMPLETED DELETE_DISK  1458853089000  1000"
    tasks = double(EsxCloud::TaskList)
    expect(client).to receive(:run_cli).with("project tasks '#{project_id}' -s 'COMPLETED'").and_return(result)
    expect(client).to receive(:get_task_list_from_response).with(result).and_return(tasks)
    client.get_project_tasks(project_id, "COMPLETED").should == tasks
  end

  it "get all project networks" do
    networks = double(EsxCloud::VirtualNetworkList)
    expect(@api_client).to receive(:get_project_networks).with("project_id", nil).and_return(networks)

    expect(client.get_project_networks("project_id")).to eq(networks)
  end

  it "get all project networks with given name" do
    networks = double(EsxCloud::VirtualNetworkList)
    expect(@api_client).to receive(:get_project_networks).with("project_id", "network_name").and_return(networks)

    expect(client.get_project_networks("project_id", "network_name")).to eq(networks)
  end
end
