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

namespace java com.vmware.photon.controller.agent.gen
namespace py gen.agent

include 'resource.thrift'
include 'roles.thrift'
include 'server_address.thrift'
include 'tracing.thrift'

// Power state
enum PowerState {
  poweredOff = 0
  poweredOn = 1
  suspended = 2
}

// Vm Cache
struct VmCache {
  1: required string name
  2: required string path
  3: required PowerState power_state
  4: required i32 memory_mb
  5: required i32 num_cpu
  6: required list<string> disks
  7: optional string tenant_id
  8: optional string project_id
}

// Task state
enum TaskState {
  error = 0
  queued = 1
  running = 2
  success = 3
}

// Task state
struct TaskCache {
  1: required TaskState state
  2: optional string error
}

// Thrift service capturing control messages to the agent.
// A control message is a high priority message that is processed in a
// thread pool of its own.

// Pings an agent for liveliness checks
struct PingRequest {
  // The scheduler ID of the sender.
  1: optional string scheduler_id
  // sequence number for this ping request. it's up to the sender to set this
  // field for debugging purpose.
  2: optional i32 sequence_number
}

// Version
struct VersionRequest {
  99: optional tracing.TracingInfo tracing_info
}

enum VersionResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct VersionResponse {
  1: required VersionResultCode result
  2: optional string error

  // agent version
  3: optional string version

  // agent revision, git commit number
  4: optional string revision
}


// Structure describing the refcount preserved with the image.
// All fields are optional for future compatibility reasons.
struct RefCount {
  // The generation number of the ref count file.
  1: optional i16 generation_num
  // Version of refcount implementation.
  // If unset assume initial version.
  2: optional byte version
  3: optional bool tombstone
  4: optional i16 ref_count
  5: optional binary vm_ids
}

// Struct describing the provisioning configuration of the esx agent.
struct ProvisionRequest {
  // This field has been deprecated.
  1: optional list<string> zookeeper_server

  // The availability zone associated with the esx host.
  2: optional string availability_zone

  // The datastores to use for cloud virtual machine workloads
  // Expects at least one datastore to be present. If this field is not set,
  // the host uses all the datastores.
  3: optional list<string> datastores

  // The networks to use for cloud virtual machine workloads
  // Specifying no networks will bring up a VM without networking.
  4: optional list<string> networks

  // The host ip + port information for the thrift services to bind to.
  // Address update will result in agent restart.
  5: optional server_address.ServerAddress address

  // List of chairman services.
  7: optional list<server_address.ServerAddress> chairman_server

  // The memory overcommit for this host. If unspecified it defaults to 1.0,
  // i.e. no overcommit
  8: optional double memory_overcommit

  // The information about the image datastore configuration
  10: optional resource.ImageDatastore image_datastore_info

  // The cpu overcommit for this host. If unspecified it defaults to 1.0,
  // i.e. no overcommit
  11: optional double cpu_overcommit

  // To specify whether a host is only used for management VMs.
  12: optional bool management_only

  // Id of the host
  13: optional string host_id

  // NTP endpoint to configure on ESX host
  14: optional string ntp_endpoint

  // A set of image datastores for this host.
  // The image_datastore_info field will be deprecated.
  15: optional set<resource.ImageDatastore> image_datastores

  99: optional tracing.TracingInfo tracing_info
}


// Provisioning result code
enum ProvisionResultCode {
  // Provisioning was successful.
  OK = 0
  // Provisioning operation cannot be performed in the current state.
  // Likely because certain properties cannot be updated.
  INVALID_STATE = 1

  // The configuration provided is invalid.
  INVALID_CONFIG = 2

  // Catch all error
  SYSTEM_ERROR = 15
}

// Provisioning response
struct ProvisionResponse {
  1: required ProvisionResultCode result
  2: optional string error
}

// Struct describing the setting availability zone of the esx host.
struct SetAvailabilityZoneRequest {
  // The availability zone associated with the esx host.
  1: required string availability_zone

  99: optional tracing.TracingInfo tracing_info
}

// SetAvailabilityZone result code
enum SetAvailabilityZoneResultCode {
  // Setting AvailabilityZone was successful.
  OK = 0

  // Catch all error
  SYSTEM_ERROR = 15
}

// SetAvailabilityZone response
struct SetAvailabilityZoneResponse {
  1: required SetAvailabilityZoneResultCode result
  2: optional string error
}

// Agent Control service
service AgentControl {
  // Parent scheduler calls ping() to check if the scheduler is running.
  void ping(1: PingRequest request)

  // Method to provision an agent for esxcloud purposes.
  ProvisionResponse provision(1: ProvisionRequest request)

  roles.GetSchedulersResponse get_schedulers(1:roles.GetSchedulersRequest request)
  VersionResponse get_version(1: VersionRequest request)

  // Method to set availability zone.
  SetAvailabilityZoneResponse set_availability_zone(1: SetAvailabilityZoneRequest request)
}
