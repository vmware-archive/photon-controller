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
  it "finds Task by id" do
    task_id = double("task1-id")
    task_hash = { "id"=>task_id,
                  "state"=>"ERROR",
                  "entity"=>{ "id"=>"disk1",
                              "kind"=>"persistent-disk"},
                  "operation"=>"CREATE_DISK",
                  "startedTime"=>1458853080000,
                  "endTime"=>1458853081000,
                  "resourceProperties"=>{"networkConnections"=>[{"ipAddress"=>"172.17.42.1", "isConnected"=>"True", "macAddress"=>"02:42:c0:3b:c9:3a", "netmask"=>"255.255.0.0", "network"=>nil}]},
                  "steps"=>[{"sequence"=>0, "operation"=>"RESERVE_RESOURCE", "state"=>"ERROR", "startedTime"=>1458853080000, "endTime"=>1458853081000, "errors"=>[{"code"=>"NotEnoughDatastoreCapacity"}]},
                           {"sequence"=>1, "operation"=>"CREATE_DISK", "state"=>"QUEUED", "startedTime"=>0, "endTime"=>0}]
                }

    task = EsxCloud::Task.create_from_hash(task_hash)
    result = "task1-id	ERROR	disk1	persistent-disk	CREATE_DISK	1458853080000	1458853081000	{\"networkConnections\":[{\"ipAddress\":\"172.17.42.1\",\"isConnected\":\"True\",\"macAddress\":\"02:42:c0:3b:c9:3a\",\"netmask\":\"255.255.0.0\",\"network\":null}]}
              0	RESERVE_RESOURCE	ERROR	1458853080000	1458853081000	NotEnoughDatastoreCapacity
              1	CREATE_DISK	QUEUED	0	0"

    expect(client).to receive(:run_cli).with("task show #{task_id}").and_return(result)
    expect(client).to receive(:get_task_from_response).with(result).and_return(task)
    expect(client.find_task_by_id(task_id)).to eq task
  end

  it "finds all tasks" do
    tasks = double(EsxCloud::TaskList)
    result = "task1 COMPLETED CREATE_TENANT  1458853080000  1000
              task2 COMPLETED DELETE_TENANT  1458853089000  1000"
    expect(client).to receive(:run_cli).with("task list -e 'tenant1' -k 'tenant' -s 'COMPLETED'").and_return(result)
    expect(client).to receive(:get_task_list_from_response).with(result).and_return(tasks)

    client.find_tasks("tenant1", "tenant", "COMPLETED").should == tasks
  end
end