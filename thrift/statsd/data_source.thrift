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

namespace cpp thrift_gen_.data_source
namespace py gen.data_source

enum QueryTermModifier {
  EQL = 1
}

struct QueryTerm {
  1: required QueryTermModifier mod = 1
  2: optional string key
  3: optional string value
}

// Represents a query over a set of data sources
struct Query {
  // Expect conjunctive normal form
  1: list<list<QueryTerm>> terms
}

struct Tuple {
  1: required string key
  2: required string value
}

struct DataSourceID {
  1: required list<Tuple> tuples
}

struct DataPoint {
  1: required i64 timestamp
  2: required string metaData
  3: required string sampleData
}

struct Sample {
  1: required DataSourceID id
  2: required list<DataPoint> dataPoints
}
