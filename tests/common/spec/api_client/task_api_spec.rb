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

  let(:http_client) do
    http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(http_client)
    http_client
  end

  let(:client) { EsxCloud::ApiClient.new("localhost:9000") }

  describe "#find_task_by_id" do
    it "finds Task" do
      task = double(EsxCloud::Task)

      expect(http_client).to receive(:get).with("/tasks/foo")
                              .and_return(ok_response("task"))
      expect(EsxCloud::Task).to receive(:create_from_json).with("task").and_return(task)

      expect(client.find_task_by_id("foo")).to eq task
    end
  end

  describe "#find_tasks" do
    context "when all params are provided" do
      it "finds Tasks" do
        tasks = double(EsxCloud::TaskList)

        expect(http_client).to receive(:get).with("/tasks?entityId=foo&entityKind=bar&state=car")
                                .and_return(ok_response("tasks"))
        expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

        expect(client.find_tasks("foo", "bar", "car")).to eq tasks
      end
    end

    context "when no params are provided" do
      it "finds Tasks" do
        tasks = double(EsxCloud::TaskList)

        expect(http_client).to receive(:get).with("/tasks")
                               .and_return(ok_response("tasks"))
        expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

        expect(client.find_tasks()).to eq tasks
      end
    end

    context "when some params are provided" do
      it "finds Tasks" do
        tasks = double(EsxCloud::TaskList)

        expect(http_client).to receive(:get).with("/tasks?entityId=foo&entityKind=bar")
                               .and_return(ok_response("tasks"))
        expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

        expect(client.find_tasks("foo", "bar")).to eq tasks
      end
    end
  end
end
