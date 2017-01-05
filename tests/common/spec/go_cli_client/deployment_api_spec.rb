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

  let(:client) do
    cmd = "/path/to/cli target set --nocertcheck localhost:9000"
    expect(EsxCloud::CmdRunner).to receive(:run).with(cmd)
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  end

  it "pauses a system under deployment" do
    expect(client).to receive(:run_cli).with("deployment pause d1")
    expect(client.pause_system("d1")).to be_nil
  end

  it "pauses background tasks under deployment" do
    expect(client).to receive(:run_cli).with("deployment pause-background-tasks d1")
    expect(client.pause_background_tasks("d1")).to be_nil
  end

  it "resumes a system under deployment" do
    expect(client).to receive(:run_cli).with("deployment resume d1")
    expect(client.resume_system("d1")).to be_nil
  end

  it "updates image datastores under deployment" do
    expect(client).to receive(:run_cli).with("deployment update-image-datastores d1 -d 'ds1,ds2'")
    expect(client.update_image_datastores("d1", ["ds1","ds2"])).to be_nil
  end
end
