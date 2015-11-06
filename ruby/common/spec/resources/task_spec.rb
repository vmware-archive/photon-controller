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

describe EsxCloud::Task do
  before(:each) do
    @task_hash = {
        "id" => "foo",
        "state" => "RUNNING",
        "entity" => {
          "id" => "bar",
          "kind" => "vm"
        }
    }
  end

  let(:client) do
    client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(client)
    client
  end

  it "delegates find_task_by_id to API client" do
    expect(client).to receive(:find_task_by_id).with("foo")
    EsxCloud::Task.find_task_by_id "foo"
  end

  it "delegates find_tasks to API client" do
    expect(client).to receive(:find_tasks).with("foo", "bar", "car")
    EsxCloud::Task.find_tasks "foo", "bar", "car"
  end

  it "can be created from hash or from JSON" do
    from_hash = EsxCloud::Task.create_from_hash(@task_hash)
    from_json = EsxCloud::Task.create_from_json(JSON.generate(@task_hash))

    [from_hash, from_json].each do |task|
      task.id.should == "foo"
      task.state.should == "RUNNING"
      task.entity_id.should == "bar"
      task.entity_kind.should == "vm"
    end
  end

  it "can be created from inventory service task hash or JSON" do
    @inventory_task_hash = {
        "id" => "foo",
        "state" => "RUNNING"
    }
    from_hash = EsxCloud::Task.create_from_hash(@inventory_task_hash)
    from_json = EsxCloud::Task.create_from_json(JSON.generate(@inventory_task_hash))

    [from_hash, from_json].each do |task|
      task.id.should == "foo"
      task.state.should == "RUNNING"
      task.entity_id.should == nil
      task.entity_kind.should == nil
    end
  end

  it "supports optional operation and errors" do
    step  =  {
      "id" => "foo",
      "state" => "RUNNING",
      "sequence" => 0,
      "operation" => "DeleteVm",
      "errors" => [
        {"code" => "100", "message" => "foo", "data" => {"a" => "b"}},
        {"code" => "200", "message" => "bar", "data" => {"c" => "d"}}
      ],
      "warnings" => [
          {"code" => "300", "message" => "foo", "data" => {"a" => "b"}},
          {"code" => "400", "message" => "bar", "data" => {"c" => "d"}}
      ]
    }
    task = EsxCloud::Task.create_from_hash(
        @task_hash.merge(
            "state" => "ERROR",
            "operation" => "do_stuff",
            "steps" => [step]
        ))
    task.operation.should == "do_stuff"
    task.errors.size.should == 1
    step_errors = task.errors.first
    step_errors.should == [
      EsxCloud::TaskError.new(step, "100", "foo", {"a" => "b"}),
      EsxCloud::TaskError.new(step, "200", "bar", {"c" => "d"})
    ]
    step_warnings = task.warnings.first
    step_warnings.should == [
        EsxCloud::TaskError.new(step, "300", "foo", {"a" => "b"}),
        EsxCloud::TaskError.new(step, "400", "bar", {"c" => "d"})
    ]
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::Task.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
    expect do
      EsxCloud::Task.create_from_hash(
        @task_hash.merge("state" => "ERROR",
                         "steps" => [{
                                       "errors" => "foo"
                                     }]))
    end.to raise_error(EsxCloud::UnexpectedFormat)

    expect do
      EsxCloud::Task.create_from_hash(@task_hash.merge("entity" => "foo"))
    end.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "has duration" do
    task = EsxCloud::Task.new("foo", "COMPLETED", "bar", "vm")
    task.duration.should be_nil
    task.start_time = 100
    task.duration.should be_nil
    task.end_time = 200
    task.duration.should == 100
  end

end
