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

  let(:api_client) do
    api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
    api_client
  end

  let(:client) { EsxCloud::CliClient.new("/path/to/cli", "localhost:9000") }

  describe "#find_task_by_id" do
    it "finds Task" do
      task = double(EsxCloud::Task)
      expect(api_client).to receive(:find_task_by_id).with("foo").and_return(task)
      expect(client.find_task_by_id("foo")).to eq task
    end
  end

  describe "#find_tasks" do
    context "when all params are provided" do
      it "finds Tasks" do
        tasks = double(EsxCloud::TaskList)
        expect(api_client).to receive(:find_tasks).with("foo", "bar", "car").and_return(tasks)
        expect(client.find_tasks("foo", "bar", "car")).to eq tasks
      end
    end

    context "when no params are provided" do
      it "finds Tasks" do
        tasks = double(EsxCloud::TaskList)
        expect(api_client).to receive(:find_tasks).with(nil, nil, nil).and_return(tasks)
        expect(client.find_tasks()).to eq tasks
      end
    end

    context "when some params are provided" do
      it "finds Tasks" do
        tasks = double(EsxCloud::TaskList)
        expect(api_client).to receive(:find_tasks).with("foo", "bar", nil).and_return(tasks)
        expect(client.find_tasks("foo", "bar")).to eq tasks
      end
    end
  end
end
