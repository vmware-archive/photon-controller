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


  it "deletes project" do
    project_id = double("aaa")

    expect(client).to receive(:run_cli).with("project delete #{project_id}")
    client.delete_project(project_id).should be_true
  end

  it "sets project security groups" do
    project_id = double("aaa")
    security_groups = {items: ["adminGroup1", "adminGroup2"]}

    expect(client).to receive(:run_cli)
                      .with("project set_security_groups '#{project_id}' '#{security_groups[:items].join(",")}'")
    client.set_project_security_groups(project_id, security_groups)
  end
end
