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

describe EsxCloud::ApiError do

  it "can be created from task" do
    task = EsxCloud::Task.create_from_hash(task_error("id", "some operation"))

    error = EsxCloud::ApiError.create_from_task(task)
    error.response_code.should == 200
    error.message.should =~ /some operation failed/
    error.errors.size.should == 1
    error.errors.first.should == [
      EsxCloud::TaskError.new({"operation" => "DeleteVm", "sequence" => 0},
                              "VmNotFound",
                              "Some message",
                              {"foo" => "bar"})
    ]
  end

  it "can be created from HTTP response" do
    http_response = EsxCloud::HttpResponse.new(404, "foo bar", {})

    error = EsxCloud::ApiError.create_from_http_error("some operation", http_response)
    error.response_code.should == 404
    error.errors.should == []
    error.message.should =~ /some operation: HTTP 404/
  end

  it "wraps error arrays into proper error structures" do
    http_response = EsxCloud::HttpResponse.new(404, JSON.generate(%w(a b)), {})

    error = EsxCloud::ApiError.create_from_http_error("some operation", http_response)
    error.response_code.should == 404
    error.errors.should == [
        EsxCloud::TaskError.new(nil, nil, "a"),
        EsxCloud::TaskError.new(nil, nil, "b")
    ]
  end

end
