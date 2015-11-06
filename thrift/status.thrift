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

namespace java com.vmware.photon.controller.status.gen
namespace py gen.status

include 'tracing.thrift'

/**
 * The enumeration of the possible status of a service.
 */
enum StatusType {
  READY = 0 // Service is ready to process requests.
  INITIALIZING = 1 // Service is still in progress to start.
  UNREACHABLE = 2 // The client times out when trying to reach service. This type is used by client.
  ERROR = 3 // Service is in error state.
  PARTIAL_ERROR = 4 // This type is used by overall ComponentStatus when one or more instance of a component
                    // is unreachable or in error state but atleast one instance is ready.
}

/**
 * The current status of the service.
 */
struct Status {
  1: required StatusType type
  2: optional string message
  3: optional map<string, string> stats
  4: optional string build_info
}

/**
 * get_status request.
 */
struct GetStatusRequest {
  99: optional tracing.TracingInfo tracing_info
}
