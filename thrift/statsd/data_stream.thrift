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

namespace cpp thrift_gen_.data_stream
namespace py gen.data_stream

include 'data_source.thrift'

struct Definition {
  1: required string id
  2: required string type

  // Data stream expiry, in nanoseconds since epoch
  3: optional i64 expires_at

  // Query to filter subset of data sources to sample
  11: required data_source.Query query

  // Interval in nanoseconds between samples, retention in nanoseconds
  21: optional i64 sample_interval
  22: optional i64 sample_retention

  // List of metrics to sample
  31: list<string> metrics
}

// Create a data stream
struct CreateRequest {
  1: required Definition definition;
}

enum CreateResultCode {
  // Data stream was created
  OK = 0

  // System error
  SYSTEM_ERROR = 1

  // The specified ID is invalid (e.g. empty)
  INVALID_ID = 2

  // The specified ID is already in use
  DUPLICATE_ID = 3

  // The specified type does not exist
  NO_SUCH_TYPE = 4
}

struct CreateResponse {
  1: required CreateResultCode result

  // Error string that must be filled in when result==SYSTEM_ERROR
  2: optional string error;
}

// List data streams
struct ListRequest {
  1: required string glob
}

enum ListResultCode {
  // Success
  OK = 0

  // System error
  SYSTEM_ERROR = 1
}

struct ListResponse {
  1: required ListResultCode result

  // Error string that must be filled in when result==SYSTEM_ERROR
  2: optional string error;

  11: list<Definition> definitions
}

// Update a data stream
struct UpdateRequest {
  1: required Definition definition
}

enum UpdateResultCode {
  // Data stream was updated
  OK = 0

  // System error
  SYSTEM_ERROR = 1

  // The specified ID does not exist
  NO_SUCH_ID = 2

  // The specified type does not match the current type
  TYPE_MISMATCH = 3
}

struct UpdateResponse {
  1: required UpdateResultCode result

  // Error string that must be filled in when result==SYSTEM_ERROR
  2: optional string error;
}

// Delete a data stream
struct DeleteRequest {
  1: required string data_stream_id
}

enum DeleteResultCode {
  // Data stream was deleted
  OK = 0

  // System error
  SYSTEM_ERROR = 1

  // The specified ID does not exist
  NO_SUCH_ID = 2
}

struct DeleteResponse {
  1: required DeleteResultCode result

  // Error string that must be filled in when result==SYSTEM_ERROR
  2: optional string error;
}

// Query a data stream
struct QueryRequest {
  // Datastream ID passed during Create
  1: required string id
  // Query to filter subset of data sources that fit the time interval
  2: required data_source.Query query
  // Time stamp of the start of the time interval in nanoseconds
  3: required i64 time_start
  // Time stamp of the end of the time interval in nanoseconds, inclusive
  4: required i64 time_end
}

enum QueryResultCode {
  // Operation successful
  OK = 0

  // Generic Error
  SYSTEM_ERROR = 1

  // The specified ID wasn't found
  NO_SUCH_ID = 2
}

struct QueryResponse {
  1: required QueryResultCode result;
  // Map with DataSources and DataPoints
  2: list<data_source.Sample> samples
}
