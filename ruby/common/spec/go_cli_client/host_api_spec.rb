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

  it "creates a host" do
    host = double(EsxCloud::Host, id: "h1", address: "10.146.36.28")
    host_id = double("h1")
    spec = EsxCloud::HostCreateSpec.new("u1", "p1", ["MGMT", "CLOUD"], "10.146.36.28", {"a" => "b"})

    expect(client).to receive(:run_cli)
                      .with("host create -u 'u1' -p 'p1' -i '10.146.36.28' -t 'MGMT,CLOUD' -m '{\"a\":\"b\"}' -d 'foo'")
                      .and_return(host_id)
    expect(client).to receive(:mgmt_find_host_by_id).with(host_id).and_return(host)
    expect(client.create_host("foo", spec.to_hash)).to eq host
  end

  it "finds mgmt host by id" do
    host_id = double("h1")
    host_hash = { "id" => host_id,
                  "username" => "u1",
                  "password" => "p1",
                  "address" => "10.146.36.28",
                  "usageTags" => "MGMT,CLOUD",
                  "state" => "READY",
                  "metadata" => {"a" => "b"} }
    host = EsxCloud::Host.create_from_hash(host_hash)
    result = "h1  u1  p1  10.146.36.28  MGMT,CLOUD  READY a:b   "

    expect(client).to receive(:run_cli).with("host show #{host_id}").and_return(result)
    expect(client).to receive(:get_host_from_response).with(result).and_return(host)
    expect(client.mgmt_find_host_by_id(host_id)).to eq host
  end

end