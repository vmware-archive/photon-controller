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
  let(:api_client) { double(EsxCloud::ApiClient) }
  let(:client) { EsxCloud::CliClient.new("/path/to/cli", URL_HOST) }

  before(:each) do
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(api_client)
    expect(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  it "creates a virtual network" do
    virtual_network = double(EsxCloud::VirtualNetwork)

    expect(api_client).to receive(:create_virtual_network)
                          .with("project1", "payload")
                          .and_return(virtual_network)
    expect(client.create_virtual_network("project1", "payload")).to eq virtual_network
  end

  it "delete a virtual network" do
    expect(api_client).to receive(:delete_virtual_network).with("network1").and_return(true)
    expect(client.delete_virtual_network("network1")).to be_true
  end

  it "find a virtual network by its id" do
    virtual_network = double(EsxCloud::VirtualNetwork)

    expect(api_client).to receive(:delete_virtual_network).with("network1").and_return(virtual_network)
    expect(client.delete_virtual_network("network1")).to eq(virtual_network)
  end
end
