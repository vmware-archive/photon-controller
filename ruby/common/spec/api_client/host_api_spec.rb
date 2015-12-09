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
  let(:host_id){
    "host_id"
  }
  let(:host_spec) {
      {
        "id" => "id0",
        "username" => "aaa"
      }
  }

  let(:metadata) {
    {
        "aa" => "bb",
        "cc" => "dd"
    }
  }

  it "creates a host" do
    host = double(EsxCloud::Host, id: "h1")

    expect(@http_client).to receive(:post_json)
                            .with("/deployments/foo/hosts", "payload")
                            .and_return(task_created("aaa"))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "h1"))
    expect(@http_client).to receive(:get).with("/hosts/h1").and_return(ok_response("host"))
    expect(EsxCloud::Host).to receive(:create_from_json).with("host").and_return(host)
    client.create_host("foo", "payload").should == host
  end

  it "shows a host" do

    expect(@http_client).to receive(:get).with("/resources/hosts/#{host_id}").and_return(ok_response(host_spec.to_json))
    client.find_host_by_id(host_id).should == host_spec
  end

  it "create host store" do
    storeName = "storeName"
    storeId = "storeId"
    expect(@http_client).to receive(:post_json).with("/resources/hosts/#{host_id}/datastores/#{storeName}/#{storeId}", {}).and_return(ok_response(host_spec.to_json))
    client.create_host_store(host_id, storeName, storeId).should == host_spec
  end

  it "delete host store" do
    storeName = "storeName"
    expect(@http_client).to receive(:delete).with("/resources/hosts/#{host_id}/datastores/#{storeName}").and_return(ok_response(host_spec.to_json))
    client.delete_host_store(host_id, storeName).should == host_spec
  end

  it "show host metadata" do

    expect(@http_client).to receive(:get).with("/resources/hosts/#{host_id}/metadata").and_return(ok_response(metadata.to_json))
    client.find_host_metadata_by_id(host_id).should == metadata
  end

  it "update host metadata" do
    property = "property0"
    value = "value0"
    expect(@http_client).to receive(:put_json).with("/resources/hosts/#{host_id}/metadata/#{property}/#{value}", {}).and_return(ok_response(host_spec.to_json))
    client.update_host_metadata_property(host_id, property, value).should == host_spec
  end

  it "update host property" do
    property = "property0"
    value = "value0"
    expect(@http_client).to receive(:put_json).with("/resources/hosts/#{host_id}/#{property}/#{value}", {}).and_return(ok_response(host_spec.to_json))
    client.update_host_property(host_id, property, value).should == host_spec
  end

  it "list hosts" do
    expect(@http_client).to receive(:get).with("/resources/hosts").and_return(ok_response(host_spec.to_json))
    client.find_all_hosts.should == host_spec
  end

  it "gets host VMs" do
    vms = double(EsxCloud::VmList)

    expect(@http_client).to receive(:get).with("/hosts/foo/vms").and_return(ok_response("vms"))
    expect(EsxCloud::VmList).to receive(:create_from_json).with("vms").and_return(vms)

    expect(client.mgmt_get_host_vms("foo")).to eq(vms)
  end

  it "list tasks for host" do
    tasks = double(EsxCloud::TaskList)
    expect(@http_client).to receive(:get).with("/hosts/#{host_id}/tasks").and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

    expect(client.find_tasks_by_host_id(host_id)).to eq(tasks)
  end

  it "host enters maintenance mode" do
    host = double(EsxCloud::Host, id: "h1")
    expect(@http_client).to receive(:post_json).with("/hosts/h1/enter_maintenance", {}).and_return(task_created("t1", 200))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/t1").and_return(task_done("t1", "h1"))
    expect(@http_client).to receive(:get).with("/hosts/h1").and_return(ok_response("host"))
    expect(EsxCloud::Host).to receive(:create_from_json).with("host").and_return(host)

    expect(client.host_enter_maintenance_mode("h1")).to eq(host)
  end

  it "host enters suspended mode" do
    host = double(EsxCloud::Host, id: "h1")
    expect(@http_client).to receive(:post_json).with("/hosts/h1/suspend", {}).and_return(task_created("t1", 200))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/t1").and_return(task_done("t1", "h1"))
    expect(@http_client).to receive(:get).with("/hosts/h1").and_return(ok_response("host"))
    expect(EsxCloud::Host).to receive(:create_from_json).with("host").and_return(host)

    expect(client.host_enter_suspended_mode("h1")).to eq(host)
  end

  it "host exits maintenance mode" do
    host = double(EsxCloud::Host, id: "h1")
    expect(@http_client).to receive(:post_json).with("/hosts/h1/exit_maintenance", {}).and_return(task_created("t1", 200))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/t1").and_return(task_done("t1", "h1"))
    expect(@http_client).to receive(:get).with("/hosts/h1").and_return(ok_response("host"))
    expect(EsxCloud::Host).to receive(:create_from_json).with("host").and_return(host)

    expect(client.host_exit_maintenance_mode("h1")).to eq(host)
  end

  it "host resumes to normal mode" do
    host = double(EsxCloud::Host, id: "h1")
    expect(@http_client).to receive(:post_json).with("/hosts/h1/resume", {}).and_return(task_created("t1", 200))
    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/t1").and_return(task_done("t1", "h1"))
    expect(@http_client).to receive(:get).with("/hosts/h1").and_return(ok_response("host"))
    expect(EsxCloud::Host).to receive(:create_from_json).with("host").and_return(host)

    expect(client.host_resume("h1")).to eq(host)
  end

end
