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

  it "creates a project" do
    project = double(EsxCloud::Project)

    expect(@http_client).to receive(:post_json)
                            .with("/tenants/foo/projects", "payload")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "project-id"))
    expect(@http_client).to receive(:get).with("/projects/project-id").and_return(ok_response("project"))
    expect(EsxCloud::Project).to receive(:create_from_json).with("project").and_return(project)

    client.create_project("foo", "payload").should == project
  end

  it "finds project by id" do
    project = double(EsxCloud::Project)

    expect(@http_client).to receive(:get).with("/projects/foo").and_return(ok_response("project"))
    expect(EsxCloud::Project).to receive(:create_from_json).with("project").and_return(project)

    client.find_project_by_id("foo").should == project
  end

  it "finds all projects" do
    projects = double(EsxCloud::ProjectList)

    expect(@http_client).to receive(:get).with("/tenants/foo/projects").and_return(ok_response("projects"))
    expect(EsxCloud::ProjectList).to receive(:create_from_json).with("projects").and_return(projects)

    client.find_all_projects("foo").should == projects
  end

  it "finds projects by name" do
    projects = double(EsxCloud::ProjectList)

    expect(@http_client).to receive(:get).with("/tenants/foo/projects?name=bar").and_return(ok_response("projects"))
    expect(EsxCloud::ProjectList).to receive(:create_from_json).with("projects").and_return(projects)

    client.find_projects_by_name("foo", "bar").should == projects
  end

  it "deletes project" do
    expect(@http_client).to receive(:delete).with("/projects/foo").and_return(task_created("aaa"))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_project("foo").should be_true
  end

  it "gets project VMs" do
    vms = double(EsxCloud::VmList)

    expect(@http_client).to receive(:get).with("/projects/foo/vms").and_return(ok_response("vms"))
    expect(EsxCloud::VmList).to receive(:create_from_json).with("vms").and_return(vms)

    client.get_project_vms("foo").should == vms
  end

  it "gets project DISKs" do
    disks = double(EsxCloud::DiskList)

    expect(@http_client).to receive(:get).with("/projects/foo/disks").and_return(ok_response("disks"))
    expect(EsxCloud::DiskList).to receive(:create_from_json).with("disks").and_return(disks)

    client.get_project_disks("foo").should == disks
  end

  it "gets project tasks" do
    tasks = double(EsxCloud::TaskList)

    expect(@http_client).to receive(:get).with("/projects/foo/tasks").and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

    client.get_project_tasks("foo").should == tasks
  end

  it "gets project clusters" do
    clusters = double(EsxCloud::ClusterList)

    expect(@http_client).to receive(:get).with("/projects/foo/clusters").and_return(ok_response("clusters"))
    expect(EsxCloud::ClusterList).to receive(:create_from_json).with("clusters").and_return(clusters)

    client.get_project_clusters("foo").should == clusters
  end

  it "gets all project networks" do
    networks = double(EsxCloud::VirtualNetworkList)

    expect(@http_client).to receive(:get).with("/projects/foo/subnets").and_return(ok_response("subsets"))
    expect(EsxCloud::VirtualNetworkList).to receive(:create_from_json).with("subsets").and_return(networks)

    expect(client.get_project_networks("foo")).to eq(networks)
  end

  it "gets all project networks with given name" do
    networks = double(EsxCloud::VirtualNetworkList)

    expect(@http_client).to receive(:get).with("/projects/foo/subnets?name=network1").and_return(ok_response("subsets"))
    expect(EsxCloud::VirtualNetworkList).to receive(:create_from_json).with("subsets").and_return(networks)

    expect(client.get_project_networks("foo", "network1")).to eq(networks)
  end

  it "sets project security groups" do
    security_groups = {items: ["adminGroup1", "adminGroup2"]}

    expect(@http_client).to receive(:post_json)
                            .with("/projects/foo/set_security_groups", security_groups)
                            .and_return(task_done("task1", "entity-id"))

    expect(@http_client).to receive(:get)
                            .with(URL_HOST + "/tasks/task1")
                            .and_return(task_done("task1", "entity-id"))

    client.set_project_security_groups("foo", security_groups)
  end

  it "gets project security groups" do
    project = EsxCloud::Project.new("foo", "name", nil, {securityGroups: ["adminGroup1", "adminGroup2"]})

    expect(@http_client).to receive(:get).with("/projects/foo").and_return(ok_response("project"))
    expect(EsxCloud::Project).to receive(:create_from_json).with("project").and_return(project)
    expect(project.security_groups).to eq({:securityGroups => ["adminGroup1", "adminGroup2"]})

    client.find_project_by_id("foo")
  end
end
