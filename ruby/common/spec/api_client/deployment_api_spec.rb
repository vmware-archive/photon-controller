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

  let(:client) do
    EsxCloud::ApiClient.new("localhost:9000")
  end
  let(:manifest_id) { "manifest_id" }
  let(:manifest) do
    {
      "id" => "id0",
      "a" => "b"
    }
  end
  let(:task) do
    {
      "id" => "id0",
      "a" => "b"
    }
  end

  it "creates a deployment" do
    deployment = double(EsxCloud::Deployment)

    expect(@http_client).to receive(:post_json)
                            .with("/deployments", "payload")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "deployment-id"))
    expect(@http_client).to receive(:get).with("/deployments/deployment-id").and_return(ok_response("deployment"))
    expect(EsxCloud::Deployment).to receive(:create_from_json).with("deployment").and_return(deployment)

    expect(client.create_api_deployment("payload")).to eq deployment
  end

  it "pauses a system under deployment" do
    deployment = double(EsxCloud::Deployment)

    expect(@http_client).to receive(:post)
                            .with("/deployments/deployment-id/pause_system", nil)
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "deployment-id"))
    expect(@http_client).to receive(:get).with("/deployments/deployment-id").and_return(ok_response("deployment"))
    expect(EsxCloud::Deployment).to receive(:create_from_json).with("deployment").and_return(deployment)

    expect(client.pause_system("deployment-id")).to eq deployment
  end

  it "resumes a system under deployment" do
    deployment = double(EsxCloud::Deployment)

    expect(@http_client).to receive(:post)
                            .with("/deployments/deployment-id/resume_system", nil)
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "deployment-id"))
    expect(@http_client).to receive(:get).with("/deployments/deployment-id").and_return(ok_response("deployment"))
    expect(EsxCloud::Deployment).to receive(:create_from_json).with("deployment").and_return(deployment)

    expect(client.resume_system("deployment-id")).to eq deployment
  end

  it "deletes a deployment" do
    expect(@http_client).to receive(:delete)
                            .with("/deployments/foo")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_api_deployment("foo").should be_true
  end

  it "updates the security_groups of a deployment" do
    security_groups = {items: ["adminGroup1", "adminGroup2"]}

    expect(@http_client).to receive(:post_json)
                            .with("/deployments/foo/set_security_groups", security_groups)
                            .and_return(task_done("task1", "entity-id"))
    expect(@http_client).to receive(:get)
                            .with(URL_HOST + "/tasks/task1")
                            .and_return(task_done("task1", "entity-id"))

    client.update_security_groups("foo", security_groups)
  end

  it "starts a deployment without given flavor" do
    expect(@http_client).to receive(:post)
                              .with("/deployment/deploy", nil)
                              .and_return(task_created("randomid"))
    expect(@http_client).to receive(:get)
                              .with(URL_HOST + "/tasks/randomid").and_return(task_done("randomid", "entity_id"))
    task = client.create_deployment(nil, false)
    expect(task.url).to eq(URL_HOST + "/tasks/randomid")
    expect(task.state).to eq("COMPLETED")
    expect(task.id).to eq("randomid")
  end

  it "starts a dry run deployment without given flavor" do
    expect(@http_client).to receive(:post)
                            .with("/deployment/deploy?dryRun=true", nil)
                            .and_return(task_created("randomid"))
    expect(@http_client).to receive(:get)
                            .with(URL_HOST + "/tasks/randomid").and_return(task_done("randomid", "entity_id"))
    task = client.create_deployment(nil, true)
    expect(task.url).to eq(URL_HOST + "/tasks/randomid")
    expect(task.state).to eq("COMPLETED")
    expect(task.id).to eq("randomid")
  end

  it "starts a deployment with given flavor" do
    expect(@http_client).to receive(:post)
                            .with("/deployment/deploy/small", nil)
                            .and_return(task_created("randomid"))
    expect(@http_client).to receive(:get)
                            .with(URL_HOST + "/tasks/randomid").and_return(task_done("randomid", "entity_id"))
    task = client.create_deployment("small", false)
    expect(task.url).to eq(URL_HOST + "/tasks/randomid")
    expect(task.state).to eq("COMPLETED")
    expect(task.id).to eq("randomid")
  end

  it "start a dry run deployment with given flavor" do
    expect(@http_client).to receive(:post)
                            .with("/deployment/deploy/small?dryRun=true", nil)
                            .and_return(task_created("randomid"))
    expect(@http_client).to receive(:get)
                            .with(URL_HOST + "/tasks/randomid").and_return(task_done("randomid", "entity_id"))
    task = client.create_deployment("small", true)
    expect(task.url).to eq(URL_HOST + "/tasks/randomid")
    expect(task.state).to eq("COMPLETED")
    expect(task.id).to eq("randomid")
  end

  it "starts a delete deployment" do
    expect(@http_client).to receive(:delete).with("/deployment")
                            .and_return(task_created("randomid"))

    task_id = client.delete_deployment(false)
    expect(task_id).to eq("randomid")
  end

  it "shows deployment manifest with given manifest id" do
    expect(@http_client).to receive(:get).with("/deployment/manifest/#{manifest_id}").and_return(ok_response(manifest.to_json))
    parsed_response = JSON.parse(client.find_manifest_by_id(manifest_id))
    parsed_response.should == manifest
  end

  it "shows the current deployment manifest" do
    expect(@http_client).to receive(:get).with("/deployment/manifest/null").and_return(ok_response(manifest.to_json))
    parsed_response = JSON.parse(client.find_manifest_by_id(nil))
    parsed_response.should == manifest
  end

  it "lists all manifests" do
    expect(@http_client).to receive(:get).with("/deployment/manifests").and_return(ok_response(task.to_json))
    client.find_all_manifests.should == task
  end

  it "lists all deployment" do
    expect(@http_client).to receive(:get).with("/deployment").and_return(ok_response(task.to_json))
    client.find_all_deployments.should == task
  end

  it "finds api deployment by id" do
    deployment = double(EsxCloud::Deployment)

    expect(@http_client).to receive(:get).with("/deployments/d1")
                            .and_return(ok_response("deployment"))
    expect(EsxCloud::Deployment).to receive(:create_from_json).with("deployment")
                                 .and_return(deployment)

    expect(client.find_deployment_by_id("d1")).to eq deployment
  end

  it "finds all api deployments" do
    deployments = double(EsxCloud::DeploymentList)

    expect(@http_client).to receive(:get).with("/deployments").and_return(ok_response("deployments"))
    expect(EsxCloud::DeploymentList).to receive(:create_from_json).with("deployments").and_return(deployments)

    expect(client.find_all_api_deployments).to eq deployments
  end

  it "gets deployment vms" do
    vms = double(EsxCloud::VmList)

    expect(@http_client).to receive(:get).with("/deployments/foo/vms").and_return(ok_response("vms"))
    expect(EsxCloud::VmList).to receive(:create_from_json).with("vms").and_return(vms)

    client.get_deployment_vms("foo").should == vms
  end

  it "gets deployment hosts" do
    hosts = double(EsxCloud::HostList)

    expect(@http_client).to receive(:get).with("/deployments/foo/hosts").and_return(ok_response("hosts"))
    expect(EsxCloud::HostList).to receive(:create_from_json).with("hosts").and_return(hosts)

    client.get_deployment_hosts("foo").should == hosts
  end

  it "configures cluster for deployment" do
    configuration = double(EsxCloud::ClusterConfiguration)

    expect(@http_client).to receive(:post_json)
                            .with("/deployments/foo/configure_cluster", "payload")
                            .and_return(ok_response("cluster_configuration"))
    expect(EsxCloud::ClusterConfiguration).to receive(:create_from_json).with("cluster_configuration")
      .and_return(configuration)

    expect(client.configure_cluster("foo", "payload")).to eq configuration
  end

end
