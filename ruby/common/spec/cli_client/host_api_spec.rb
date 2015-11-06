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

  it "creates a host" do
    host = double(EsxCloud::Host, id: "h1", address: "10.146.36.28")
    spec = EsxCloud::HostCreateSpec.new("u1", "p1", ["MGMT", "CLOUD"], "10.146.36.28", {"a" => "b"}, "z1")

    expect(client).to receive(:run_cli).with("host create -u 'u1' -t 'p1' -i '10.146.36.28' -z 'z1' -t 'MGMT,CLOUD' -m '{\"a\":\"b\"}'")
    expect(client).to receive(:mgmt_find_all_hosts).and_return(EsxCloud::HostList.new([host]))
    expect(client.create_host("foo", spec.to_hash)).to eq host
  end

  it "finds mgmt host by id" do
    host = double(EsxCloud::Host)

    expect(api_client).to receive(:mgmt_find_host_by_id).with("h1")
                            .and_return(host)
    expect(client.mgmt_find_host_by_id("h1")).to eq host
  end

  it "List all tasks for a host" do
    task = double(EsxCloud::TaskList)

    expect(api_client).to receive(:find_tasks_by_host_id).with("h1").and_return(task)
    expect(client.find_tasks_by_host_id("h1")).to eq task
  end

  it "host enters maintenance mode" do
    host = double(EsxCloud::Host)

    expect(api_client).to receive(:host_enter_maintenance_mode).with("h1").and_return(host)
    expect(client.host_enter_maintenance_mode("h1")).to eq host
  end

  it "host enters suspended mode" do
    host = double(EsxCloud::Host)

    expect(api_client).to receive(:host_enter_suspended_mode).with("h1").and_return(host)
    expect(client.host_enter_suspended_mode("h1")).to eq host
  end

  it "host exits maintenance mode" do
    host = double(EsxCloud::Host)

    expect(api_client).to receive(:host_exit_maintenance_mode).with("h1").and_return(host)
    expect(client.host_exit_maintenance_mode("h1")).to eq host
  end

  it "host resumes to normal mode" do
    host = double(EsxCloud::Task)

    expect(api_client).to receive(:host_resume).with("h1").and_return(host)
    expect(client.host_resume("h1")).to eq host
  end
end
