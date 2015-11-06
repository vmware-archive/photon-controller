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
  let(:client) { EsxCloud::CliClient.new("/path/to/cli", "localhost:9000") }

  before(:each) do
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  it "finds portgroup by id" do
    portgroup = double(EsxCloud::Network)

    expect(api_client).to receive(:find_portgroup_by_id).with("p1")
                          .and_return(portgroup)
    expect(client.find_portgroup_by_id("p1")).to eq portgroup
  end

  it "finds portgroups" do
    portgroups = double(EsxCloud::PortGroupList)

    expect(api_client).to receive(:find_portgroups).with("p1", "CLOUD").and_return(portgroups)
    expect(client.find_portgroups("p1", "CLOUD")).to eq portgroups
  end

end
