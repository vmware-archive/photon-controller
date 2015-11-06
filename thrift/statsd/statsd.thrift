/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

namespace cpp thrift_gen_
namespace py gen.statsd

include 'data_stream.thrift'

struct TestRequest {
  1: required string name
}

enum TestResultCode {
  OK = 0
}

struct TestResponse {
  1: required TestResultCode result
}

service statsd {
  TestResponse test(1: TestRequest request)

  // Data stream CRUD
  data_stream.CreateResponse DataStreamCreate(1: data_stream.CreateRequest request)
  data_stream.ListResponse   DataStreamList  (1: data_stream.ListRequest   request)
  data_stream.UpdateResponse DataStreamUpdate(1: data_stream.UpdateRequest request)
  data_stream.DeleteResponse DataStreamDelete(1: data_stream.DeleteRequest request)
  data_stream.QueryResponse  DataStreamQuery (1: data_stream.QueryRequest request)
}
