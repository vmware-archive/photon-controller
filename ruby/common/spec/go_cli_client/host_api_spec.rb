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

  it "gets host tasks" do
    host_id = double("h1")
    result = "task1 COMPLETED CREATE_HOST  1458853080000  1000
              task2 COMPLETED DELETE_HOST  1458853089000  1000"
    tasks = double(EsxCloud::TaskList)
    expect(client).to receive(:run_cli).with("host tasks '#{host_id}'").and_return(result)
    expect(client).to receive(:get_task_list_from_response).with(result).and_return(tasks)
    client.find_tasks_by_host_id(host_id).should == tasks
  end

  it "deletes a host" do
    host_id = double("bar")
    expect(client).to receive(:run_cli).with("host delete '#{host_id}'")

    client.mgmt_delete_host(host_id).should be_true
  end

  it "gets all vms in the specified host" do
    host_id = double("bar")
    vms = double(EsxCloud::VmList)
    result ="hostId1  host1 READY
             hostId2  host2 READY"
    expect(client).to receive(:run_cli).with("host list-vms '#{host_id}'").and_return(result)
    expect(client).to receive(:get_vm_list_from_response).with(result).and_return(vms)

    client.mgmt_get_host_vms(host_id).should == vms
  end

  it "set availability zone for the specified host" do
    host_id = double("h1")
    host = double(EsxCloud::Host, id: host_id, address: "10.146.36.28")
    payload = {availability_zone: "availabilityzone1"}

    expect(client).to receive(:run_cli)
                      .with("host set-availability-zone '#{host_id}' '#{payload[:availability_zone]}'")
                      .and_return(host_id)
    expect(client).to receive(:mgmt_find_host_by_id).with(host_id).and_return(host)
    client.host_set_availability_zone(host_id, payload).should == host
  end

  it "host enters maintenance mode" do
    host_id = double("h1")
    host = double(EsxCloud::Host, id: host_id)

    expect(client).to receive(:run_cli).with("host enter-maintenance '#{host_id}'").and_return(host_id)
    expect(client).to receive(:mgmt_find_host_by_id).with(host_id).and_return(host)

    expect(client.host_enter_maintenance_mode(host_id)).to eq(host)
  end

  it "host enters suspended mode" do
    host_id = double("h1")
    host = double(EsxCloud::Host, id: host_id)

    expect(client).to receive(:run_cli).with("host suspend '#{host_id}'").and_return(host_id)
    expect(client).to receive(:mgmt_find_host_by_id).with(host_id).and_return(host)

    expect(client.host_enter_suspended_mode(host_id)).to eq(host)
  end

  it "host exits maintenance mode" do
    host_id = double("h1")
    host = double(EsxCloud::Host, id: host_id)

    expect(client).to receive(:run_cli).with("host exit-maintenance '#{host_id}'").and_return(host_id)
    expect(client).to receive(:mgmt_find_host_by_id).with(host_id).and_return(host)

    expect(client.host_exit_maintenance_mode(host_id)).to eq(host)
  end

  it "host resumes to normal mode" do
    host_id = double("h1")
    host = double(EsxCloud::Host, id: host_id)

    expect(client).to receive(:run_cli).with("host resume '#{host_id}'").and_return(host_id)
    expect(client).to receive(:mgmt_find_host_by_id).with(host_id).and_return(host)

    expect(client.host_resume(host_id)).to eq(host)
  end
end
