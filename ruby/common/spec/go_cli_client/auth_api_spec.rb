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

  it "get auth info" do
    auth_hash = { "enabled" => true,
                  "endpoint" => "endpoint1",
                  "port" => 1234 }
    auth_info = EsxCloud::AuthInfo.create_from_hash(auth_hash)
    result = "true  endpoint1 1234"
    expect(client).to receive(:run_cli).with("auth show").and_return(result)
    expect(client).to receive(:get_auth_info_from_response).with(result).and_return(auth_info)

    expect(client.get_auth_info).to eq(auth_info)
  end

end
