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

URL_HOST = "http://localhost:9000"

require "simplecov"
require "simplecov-rcov"

SimpleCov.formatter = SimpleCov::Formatter::MultiFormatter[
    SimpleCov::Formatter::HTMLFormatter,
    SimpleCov::Formatter::RcovFormatter
]

SimpleCov.start do
  coverage_dir "reports/coverage"
  add_filter "/spec"
end

require "rspec"

require_relative "../lib/common"

RSpec.configure do |config|
  config.color = true
  config.formatter = :documentation

  config.before(:each) do
    # Mocking out config singleton so each test gets a pristine one
    # but still exposing the real one in case we need to test it.
    @real_config = EsxCloud::Config
    stub_const("EsxCloud::Config", double())
  end
end

def task_created(task_id, code = 201)
  task_hash = {
      "id" => task_id,
      "selfLink" => URL_HOST + "/tasks/#{task_id}",
      "startedTime" => "1",
      "state" => "QUEUED"
  }
  EsxCloud::HttpResponse.new(code, JSON.generate(task_hash), {})
end

def task_done(task_id, entity_id, entity_kind = "", resource_properties = nil)
  task_hash = {
      "id" => task_id,
      "selfLink" => URL_HOST + "/tasks/#{task_id}",
      "entity" => {
        "id" => entity_id,
        "kind" => entity_kind
      },
      "state" => "COMPLETED",
      "resourceProperties" => resource_properties
  }
  EsxCloud::HttpResponse.new(200, JSON.generate(task_hash), {})
end



def task_error(id, operation)
  JSON.parse %Q(
{
    "id": "#{id}",
    "selfLink": "#{URL_HOST}tasks/#{id}",
    "entity": {
        "kind": "vm",
        "id": "entity-id"
    },
    "resourceProperties": null,
    "state": "ERROR",
    "steps": [
        {
            "id": "id-1",
            "sequence": 0,
            "state": "ERROR",
            "errors": [
                {
                    "code": "VmNotFound",
                    "message": "Some message",
                    "data": {
                        "foo": "bar"
                    }
                }
            ],
            "operation": "DeleteVm",
            "queuedTime": 10,
            "startedTime": 11,
            "endTime": 12
        },
        {
            "id": "id-2",
            "sequence": 1,
            "state": "QUEUED",
            "errors": [],
            "operation": "CreateVm",
            "queuedTime": 10,
            "startedTime": 11,
            "endTime": 12,
            "options": null
        }
    ],
    "operation": "#{operation}",
    "queuedTime": 10,
    "startedTime": 11,
    "endTime": 12
}
    )
end

def ok_response(body, status=200)
  EsxCloud::HttpResponse.new(status, body, {})
end

def created_response(body, status=201)
  EsxCloud::HttpResponse.new(status, body, {})
end

def with_env(env = {})
  old_env = {}

  env.each do |key, value|
    old_env[key] = ENV[key]
    ENV[key] = value
  end

  begin
    yield
  ensure
    old_env.each do |key, value|
      ENV[key] = value
    end
  end
end
