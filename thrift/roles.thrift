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

namespace java com.vmware.photon.controller.roles.gen
namespace py gen.roles

include 'resource.thrift'

struct ChildInfo {
  // Either a scheduler ID or an agent ID.
  1: required string id

  // IP address or hostname of this host.
  2: required string address

  // Port this host listens to.
  3: required i32 port

  // Resource constraints
  4: optional list<resource.ResourceConstraint> constraints

  // The total number of hosts if this is a leaf scheduler
  5: optional i32 weight

  // The id of the owner host if id is a scheduler id
  6: optional string owner_host
}

struct SchedulerRole {
  1: required string id
  2: optional string parent_id  // null for root scheduler
  3: optional list<string> schedulers
  4: optional list<string> hosts
  // The following fields are temporary. We are moving away from reading
  // host/scheduler host:port from ZooKeeper. We'll replace schedulers and
  // hosts fields once scheduler code gets updated.
  5: optional list<ChildInfo> scheduler_children
  6: optional list<ChildInfo> host_children
}

struct Roles {
  1: optional list<SchedulerRole> schedulers
}

struct GetSchedulersRequest {
}

enum GetSchedulersResultCode {
  // successfully returning list of schedulers
  OK = 0

  SYSTEM_ERROR = 1

  // this chairman is not currently a chairman leader
  NOT_LEADER = 2

  // This chairman is not in the majority
  NOT_IN_MAJORITY = 3
}

struct SchedulerEntry {
  1: required SchedulerRole role

  // might not be set if the role hasn't been assigned yet
  2: optional string agent
}

struct GetSchedulersResponse {
  1: required GetSchedulersResultCode result
  2: optional string error
  3: optional list<SchedulerEntry> schedulers
}
