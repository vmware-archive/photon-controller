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

require_relative "spec_helper"

describe EsxCloud::ApiClient do

  class TestTaskTracker
    def task_progress(_)
    end

    def task_done(_)
    end
  end

  before(:each) do
    @http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(@http_client)
  end

  let(:client) {
    EsxCloud::ApiClient.new("localhost:9000")
  }

  def task_response(id, state, entity_id, entity_kind)
    {
        "id" => id,
        "state" => state,
        "entity" => {
          "id" => entity_id,
          "kind" => entity_kind
        }
    }
  end

  def http_response(http_code, payload = {}, header = {})
    EsxCloud::HttpResponse.new(http_code, JSON.generate(payload), header)
  end

  it "should have all methods defined by client spec" do
    base_client = EsxCloud::Client.new

    base_client.public_methods(false).each do |method_name|
      client.respond_to?(method_name).should be_true, "#{method_name} does not exist"
      client.method(method_name).arity.should == base_client.method(method_name).arity
    end
  end

  it "supports injecting a task tracker" do
    expect {
      EsxCloud::ApiClient.new("localhost:9000", "foo")
    }.to raise_error(ArgumentError, /invalid task tracker/i)

    tracker = TestTaskTracker.new
    EsxCloud::ApiClient.new("localhost:9000", tracker).task_tracker.should == tracker
  end

  it "needs task URL to poll" do
    expect {
      client.poll_task(nil)
    }.to raise_error(EsxCloud::Error, /no task URL/)
  end

  it "can time-out while polling task" do
    expect {
      client.poll_task(EsxCloud::HttpResponse.new(200, "", {"location" => "/tasks/foo"}), %w(queued), 0)
    }.to raise_error(EsxCloud::Error, /timed out/i)
  end

  it "fails when gets non-200 while polling task" do
    initial_response = http_response(200, {"id" => "foo"}, {})

    poll_responses = [
        http_response(200, task_response("foo", "STARTED", "bar", "vm")),
        http_response(404, [])
    ]

    expect(@http_client).to receive(:get).exactly(13).times.with("/tasks/foo").and_return(*poll_responses)

    expect {
      client.poll_response(initial_response, %w(queued started), 100, 0)
    }.to raise_error(EsxCloud::Error, /non-ok response/i)
  end

  it "calls back task tracker methods when appropriate" do
    initial_response = http_response(200, {"id" => "foo"}, {})

    poll_responses = [
        http_response(200, task_response("foo", "STARTED", "bar", "vm")),
        http_response(200, task_response("foo", "DONE", "bar", "vm"))
    ]

    allow(@http_client).to receive(:get).with("/tasks/foo").and_return(*poll_responses)
    tracker = TestTaskTracker.new

    expect(tracker).to receive(:task_progress).twice
    expect(tracker).to receive(:task_done).once

    client.task_tracker = tracker
    client.poll_response(initial_response, %w(queued started), 100, 0)
  end

  it "supports response code checking" do
    response = EsxCloud::HttpResponse.new(200, "", "location" => "/tasks/foo")

    expect {
      client.check_response("some operation", response, [200, 201])
    }.not_to raise_error

    expect {
      client.check_response("some operation", response, [201])
    }.to raise_error(EsxCloud::ApiError)
  end

  it "supports task polling" do
    initial_response = http_response(200, {"id" => "foo"}, {})

    poll_responses = [
        http_response(200, task_response("foo", "QUEUED", "bar", "vm")),
        http_response(200, task_response("foo", "STARTED", "bar", "vm")),
        http_response(200, task_response("foo", "started", "bar", "vm")),
        http_response(200, task_response("foo", "DONE", "bar", "vm"))
    ]

    allow(@http_client).to receive(:get).with("/tasks/foo").and_return(*poll_responses)

    task = client.poll_response(initial_response, %w(queued started), 100, 0)

    task.should be_a(EsxCloud::Task)
    task.entity_id.should == "bar"
    task.entity_kind.should == "vm"
    task.id.should == "foo"
    task.state.should == "DONE"
  end

  it "supports task errors" do
    initial_response = http_response(200, {"id" => "foo"}, {})
    error_payload = task_error("id", "foo")
    allow(@http_client).to receive(:get).with("/tasks/foo").and_return(http_response(200, error_payload))

    begin
      client.poll_response(initial_response, %w(queued started), 100, 0)
      fail("Should have raised exception for task error state")
    rescue EsxCloud::ApiError => e
      e.message.should =~ /^foo failed/
      e.errors.size.should == 1
      step_errors = e.errors.first
      step_errors.should be_a(Array)
      step_errors.first.should be_a(EsxCloud::TaskError)
    end
  end

  it "finds task by id" do
    task = double(EsxCloud::Task)

    expect(@http_client).to receive(:get).with("/tasks/foo").and_return(ok_response("task"))
    expect(EsxCloud::Task).to receive(:create_from_json).with("task").and_return(task)

    client.find_task_by_id("foo").should == task
  end

  it "finds task context by id" do
    context = {"c" => "d"}
    expect(@http_client).to receive(:get).with("/tasks/foo/context").and_return(ok_response(context.to_json))
    parsed_response = JSON.parse(client.find_task_context_by_id("foo"))
    parsed_response.should == context
  end

  it "should create httpClient with access_token" do
    endpoint = "http://foo"
    access_token = "bar"
    tracker = TestTaskTracker.new

    expect(EsxCloud::HttpClient).to receive(:new).with(endpoint, access_token)
    EsxCloud::ApiClient.new(endpoint, tracker, access_token)
  end

end
