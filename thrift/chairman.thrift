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

namespace java com.vmware.photon.controller.chairman.gen
namespace py gen.chairman

// Host registration: allows hosts to register themselves with chairman.
// Host can provide its current roles as a hint: chairman will try to minimize
// host reconfigurations by letting hosts keep their roles as long as it keeps
// the system in a good state.

include 'host.thrift'
include 'resource.thrift'
include 'roles.thrift'
include 'status.thrift'

struct RegisterHostRequest {
  1: required string id
  2: required host.HostConfig config
}

enum RegisterHostResultCode {
  // Registration was successful
  OK = 0

  SYSTEM_ERROR = 1

  // Host provided an invalid fault domain
  INVALID_FAULT_DOMAIN = 2

  // This chairman is not currently a chairman leader
  NOT_LEADER = 3

  // This chairman is not in the majority
  NOT_IN_MAJORITY = 4
}

struct RegisterHostResponse {
  1: required RegisterHostResultCode result
  2: optional string error
}

// Health check: schedulers can report which hosts/child schedulers went down,
// so chairman can fix its own system state representation.
struct ReportMissingRequest {
  1: required string scheduler_id
  2: optional list<string> schedulers
  3: optional list<string> hosts
}

enum ReportMissingResultCode {
  // Request has been processed
  OK = 0

  SYSTEM_ERROR = 1

  // This chairman is not currently a chairman leader
  NOT_LEADER = 2

  // This chairmain in not in the majority
  NOT_IN_MAJORITY = 3
}

struct ReportMissingResponse {
  1: required ReportMissingResultCode result
  2: optional string error
}

// Called by schedulers when their missing children come back alive.
struct ReportResurrectedRequest {
  1: required string scheduler_id
  2: optional list<string> schedulers
  3: optional list<string> hosts
}

enum ReportResurrectedResultCode {
  // successfully returning list of schedulers
  OK = 0

  SYSTEM_ERROR = 1

  // This chairmain in not in the majority
  NOT_IN_MAJORITY = 2
}

struct ReportResurrectedResponse {
  1: required ReportResurrectedResultCode result
  2: optional string error
}

// Called by hosts to unregister a host during maintenance mode
struct UnregisterHostRequest {
  1: required string id
}

enum UnregisterHostResultCode {
  // Unregister was successful
  OK = 0

  SYSTEM_ERROR = 1

  // This chairman is not in the majority
  NOT_IN_MAJORITY = 2
}

struct UnregisterHostResponse {
  1: required UnregisterHostResultCode result
  2: optional string error
}

service Chairman {
  roles.GetSchedulersResponse get_schedulers(1:roles.GetSchedulersRequest request)
  status.Status get_status(1:status.GetStatusRequest request)
  RegisterHostResponse register_host(1:RegisterHostRequest request)
  ReportMissingResponse report_missing(1:ReportMissingRequest request)
  ReportResurrectedResponse report_resurrected(1:ReportResurrectedRequest request)
  UnregisterHostResponse unregister_host(1:UnregisterHostRequest request)
}
