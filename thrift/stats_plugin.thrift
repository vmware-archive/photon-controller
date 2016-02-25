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

namespace java com.vmware.photon.controller.stats.plugin.gen
namespace py gen.stats.plugin

struct StatsPluginConfig {
  1: required string store_endpoint
  2: required i32 store_port
}

// Stats request: change the collection level of the stats collector
struct SetCollectionLevelRequest {
  1: required i32 level

  99: optional string tracing_info
}

enum SetCollectionLevelResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  INVALID_LEVEL = 2
}

struct SetCollectionLevelResponse {
  1: required SetCollectionLevelResultCode result
  2: optional string error
}

service StatsService {
  SetCollectionLevelResponse set_collection_level(1: SetCollectionLevelRequest request)
}
