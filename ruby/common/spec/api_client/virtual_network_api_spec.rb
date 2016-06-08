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

  let(:client) {
    EsxCloud::ApiClient.new(URL_HOST)
  }
  let(:http_client) {
    double(EsxCloud::HttpClient)
  }

  before(:each) do
    EsxCloud::HttpClient.stub(:new).and_return(http_client)
  end

  it "creates a virtual network" do
    virtual_network = double(EsxCloud::VirtualNetwork)

    expect(http_client).to receive(:post_json)
                           .with("/projects/project1/networks", "payload")
                           .and_return(task_created("task1"))
    expect(http_client).to receive(:get)
                           .with(URL_HOST + "/tasks/task1")
                           .and_return(task_done("task1", "network1"))
    expect(http_client).to receive(:get)
                           .with("/networks/network1")
                           .and_return(ok_response("network"))
    expect(EsxCloud::VirtualNetwork).to receive(:create_from_json)
                                        .with("network")
                                        .and_return(virtual_network)

    expect(client.create_virtual_network("project1", "payload")).to eq virtual_network
  end

  it "deletes a virtual network" do
    expect(http_client).to receive(:delete)
                           .with("/networks/network1")
                           .and_return(task_created("task1"))
    expect(http_client).to receive(:get)
                           .with(URL_HOST + "/tasks/task1")
                           .and_return(task_done("task1", "network1"))

    expect(client.delete_virtual_network("network1")).to be_true
  end

  it "finds a virtual network by its id" do
    virtual_network = double(EsxCloud::VirtualNetwork)

    expect(http_client).to receive(:get)
                           .with("/networks/network1")
                           .and_return(ok_response("network"))
    expect(EsxCloud::VirtualNetwork).to receive(:create_from_json)
                                        .with("network")
                                        .and_return(virtual_network)

    expect(client.find_virtual_network_by_id("network1")).to eq virtual_network
  end
end
