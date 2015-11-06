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

  it "creates a project" do
    project = double(EsxCloud::Project, :id => "aaa")
    tenant = double(EsxCloud::Tenant, :name => "t1")
    projects = double(EsxCloud::ProjectList, :items => [project])

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

    expect(@api_client).to receive(:find_tenant_by_id).with("foo").and_return(tenant)

    expect(client).to receive(:run_cli).with("project create -t 't1' -n 'p1' -r 'bar' -l 'a b c, d e f'")
    expect(client).to receive(:find_projects_by_name).and_return(projects)

    client.create_project("foo", spec).should == project
    client.project_to_tenant["aaa"].should == tenant
  end

  it "finds project by id" do
    project = double(EsxCloud::Project)
    expect(@api_client).to receive(:find_project_by_id).with("foo").and_return(project)
    client.find_project_by_id("foo").should == project
  end

  it "finds all projects" do
    projects = double(EsxCloud::ProjectList)
    expect(@api_client).to receive(:find_all_projects).with("foo").and_return(projects)
    client.find_all_projects("foo").should == projects
  end

  it "finds projects by name" do
    projects = double(EsxCloud::ProjectList)
    expect(@api_client).to receive(:find_projects_by_name).with("foo", "bar").and_return(projects)
    client.find_projects_by_name("foo", "bar").should == projects
  end

  it "deletes project" do
    client.project_to_tenant["foo"] = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :name => "p1")

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli).with("project delete 'p1' -t 't1'")

    client.delete_project("foo").should be_true
  end

  it "leverages API client to delete project if there's not enough data to use CLI" do
    project = double(EsxCloud::Project, :name => "p1")

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(@api_client).to receive(:delete_project).with("foo")

    client.delete_project("foo").should be_true
  end

  it "gets project VMs" do
    vms = double(EsxCloud::VmList)
    expect(@api_client).to receive(:get_project_vms).with("foo").and_return(vms)
    client.get_project_vms("foo").should == vms
  end

  it "gets project DISKs" do
    disks = double(EsxCloud::DiskList)
    expect(@api_client).to receive(:get_project_disks).with("foo").and_return(disks)
    client.get_project_disks("foo").should == disks
  end

  it "gets project tasks" do
    tasks = double(EsxCloud::TaskList)
    expect(@api_client).to receive(:get_project_tasks).with("foo", "a", "b").and_return(tasks)
    client.get_project_tasks("foo", "a", "b").should == tasks
  end

  it "gets project clusters" do
    clusters = double(EsxCloud::ClusterList)
    expect(@api_client).to receive(:get_project_clusters).with("foo").and_return(clusters)
    client.get_project_clusters("foo").should == clusters
  end

  it "sets project security groups" do
    security_groups = {items: ["adminGroup1", "adminGroup2"]}
    expect(@api_client).to receive(:set_project_security_groups).with("foo", security_groups)

    client.set_project_security_groups("foo", security_groups)
  end
end
