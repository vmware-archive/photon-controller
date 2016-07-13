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
include 'server_address.thrift'
include 'stats_plugin.thrift'
include 'tracing.thrift'

// Vm Cache
struct VmCache {
  1: required string name
  2: required string path
  3: required resource.VmPowerState power_state
  4: required i32 memory_mb
  5: required i32 num_cpu
  6: required list<string> disks
  7: required string location_id
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
  // The datastores to use for cloud virtual machine workloads
  // Expects at least one datastore to be present. If this field is not set,
  // the host uses all the datastores.
  3: optional list<string> datastores

  // The host ip + port information for the thrift services to bind to.
  // Address update will result in agent restart.
  5: optional server_address.ServerAddress address

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

  // Configuration of the Stats Plugin
  16: optional stats_plugin.StatsPluginConfig stats_plugin_config

  // Id of the deployment
  17: optional string deployment_id

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

// Upgrade request
struct UpgradeRequest {
  99: optional tracing.TracingInfo tracing_info
}

// Upgrade result code
enum UpgradeResultCode {
  // Upgrade started.
  OK = 0

  // Catch all error
  SYSTEM_ERROR = 1
}

// Upgrade response
struct UpgradeResponse {
  1: required UpgradeResultCode result
  2: optional string error
}

// The current status of the agent
enum AgentStatusCode {
   // The agent is up and running and can accept thrift calls.
   OK = 0

   // The agent is in the process of restarting
   RESTARTING = 1

   // The agent is in the process of upgrading
   UPGRADING = 2
}

// Agent status response
struct AgentStatusResponse {
  1: required AgentStatusCode status
}

// Agent Control service
service AgentControl {
  // Parent scheduler calls ping() to check if the scheduler is running.
  void ping(1: PingRequest request)

  // Method to provision an agent for esxcloud purposes.
  ProvisionResponse provision(1: ProvisionRequest request)

  // Method to upgrade an agent.
  UpgradeResponse upgrade(1: UpgradeRequest request)

  // Get the status of the agent.
  AgentStatusResponse get_agent_status()

  VersionResponse get_version(1: VersionRequest request)
}
