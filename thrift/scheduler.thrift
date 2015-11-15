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

namespace java com.vmware.photon.controller.scheduler.gen
namespace py gen.scheduler

include 'resource.thrift'
include 'roles.thrift'
include 'server_address.thrift'
include 'tracing.thrift'

// Place parameters
// send the scheduler parameters along with the request
// this allows changing the scheduler behavior without
// messing around with config files on root-schedulers
// and agents
struct PlaceParams {
  1: required double fanoutRatio
  2: required i32 maxFanoutCount
  3: required i32 minFanoutCount
  4: required i64 timeout
  5: required double fastPlaceResponseTimeoutRatio
  6: required double fastPlaceResponseRatio
  7: required i32 fastPlaceResponseMinCount
}

// Same as above for find
struct FindParams {
  1: required i32 timeout
}

// Place a resource
struct PlaceRequest {
  1: required resource.Resource resource
  2: optional string scheduler_id
  3: optional PlaceParams rootSchedulerParams
  4: optional PlaceParams leafSchedulerParams
  99: optional tracing.TracingInfo tracing_info
}

enum PlaceResultCode {
  // Resources are placed, agent_id, score, and generation are set
  OK = 0

  SYSTEM_ERROR = 1

  // Root scheduler is not the current leader, resources are not placed
  // Does not apply to branch schedulers
  NOT_LEADER = 2

  // Host does not have such resource
  NO_SUCH_RESOURCE = 3

  // Host does not have enough CPU resource
  NOT_ENOUGH_CPU_RESOURCE = 4

  // Host does not have enough memory resource
  NOT_ENOUGH_MEMORY_RESOURCE = 5

  // Datastore does not have enough capacity
  NOT_ENOUGH_DATASTORE_CAPACITY = 6

  // Invalid scheduler id, only applies to branch schedulers
  INVALID_SCHEDULER = 7

  // Host in invalid state, for example, Maintenance mode. Only applies to host.
  INVALID_STATE = 8

  // Resources are not placed due to resource constraints
  RESOURCE_CONSTRAINT = 9
}

struct Score {
  1: required i32 utilization
  2: required i32 transfer
}

struct PlaceResponse {
  1: required PlaceResultCode result

  2: optional string error

  // Agent that was chosen for the requested PlaceSpec
  3: optional string agent_id

  // Placement score, only used by scheduler hiearachy
  4: optional Score score

  // Generation version used by reservations to avoid a hurd on a single agent
  5: optional i32 generation

  // Placement plan (list of placements)
  6: optional resource.ResourcePlacementList placementList

  // host:port of the agent that was chosen for the requested PlaceSpec. This
  // field is set if and only if the place request was successful.
  7: optional server_address.ServerAddress address

  // Tracing info
  99: optional tracing.TracingInfo tracing_info
}

// Find a resource
struct FindRequest {
  1: required resource.Locator locator
  2: optional string scheduler_id
  3: optional FindParams params
  99: optional tracing.TracingInfo tracing_info
}

enum FindResultCode {
  // Resource is found, agent id set
  OK = 0

  SYSTEM_ERROR = 1

  // Root scheduler is not the current leader, resources are not placed
  // Does not apply to intermeddiate schedulers
  NOT_LEADER = 2

  // Resource not found
  NOT_FOUND = 3

  // Invalid scheduler id, only applies to branch schedulers
  INVALID_SCHEDULER = 4

  // Resource is found, but can not get detailed information of that resource.
  // Maybe hostd is not responding, while agent is still working.
  MISSING_DETAILS = 5
}

struct FindResponse {
  1: required FindResultCode result

  2: optional string error

  // Agent that has the requested resource
  3: optional string agent_id

  // Datastore that has the requested resource
  4: optional resource.Datastore datastore

  // Path of VM or disk
  5: optional string path

  // host:port of the agent that has the requested resource. This field is set
  // if and only if the find request was successful.
  6: optional server_address.ServerAddress address
}

// Scheduler service
service Scheduler {
  PlaceResponse place(1: PlaceRequest request)
  FindResponse find(1: FindRequest request)

  // place/find handlers for hosts. These methods are in the Scheduler service
  // as opposed to the Host service to avoid getting stuck behind slow create_vm
  // requests.
  PlaceResponse host_place(1: PlaceRequest request)
  FindResponse host_find(1: FindRequest request)
}

// Configure host and root scheduler
enum ConfigureResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  NOT_LEADER = 2
}

struct ConfigureResponse {
  1: required ConfigureResultCode result
  2: optional string error
}

struct ConfigureRequest {
  // Parent Scheduler (Leaf)
  1: required string scheduler

  // Assigned role configuration
  2: required roles.Roles roles

  // Host Id
  3: optional string host_id

  99: optional tracing.TracingInfo tracing_info
}
