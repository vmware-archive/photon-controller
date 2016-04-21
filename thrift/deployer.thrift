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

namespace java com.vmware.photon.controller.deployer.gen
namespace py gen.deployer

include 'tracing.thrift'
include 'status.thrift'
include 'resource.thrift'

/**
 * createHost endpoint definition
 */
struct CreateHostRequest {
  1:  required resource.Host host
  99: optional tracing.TracingInfo tracing_info
}

enum CreateHostResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
  EXIST_HOST_WITH_SAME_ADDRESS = 3
  HOST_NOT_FOUND = 4
  HOST_LOGIN_CREDENTIALS_NOT_VALID = 5
  MANAGEMENT_VM_ADDRESS_ALREADY_IN_USE = 6
}

struct CreateHostResult {
  1: required CreateHostResultCode code
  2: optional string error
}

struct CreateHostResponse {
  1: required CreateHostResult result
  2: optional string operation_id
}

enum CreateHostStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct CreateHostStatus {
  1: required CreateHostStatusCode code
  2: optional string error
}

struct CreateHostStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct CreateHostStatusResponse {
  1: required CreateHostResult result
  2: optional CreateHostStatus status
}

// Delete Host
struct DeleteHostRequest {
  1: required string host_id
  99: optional tracing.TracingInfo tracing_info
}

enum DeleteHostResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  HOST_NOT_FOUND = 2
  OPERATION_NOT_ALLOWED = 3
  CONCURRENT_HOST_OPERATION = 90
}

struct DeleteHostResponse {
  1: required DeleteHostResultCode result
  2: optional string error
}

/**
 * deploy endpoint definition
 */
struct Deployment {
  1:  required string id

  5:  required string imageDatastore
  6:  required bool useImageDatastoreForVms

  10: optional string ntpEndpoint

  15: optional string syslogEndpoint

  20: required bool authEnabled
  21: optional string oauthEndpoint
  22: optional string oauthTenant
  23: optional string oauthUsername
  24: optional string oauthPassword
  25: optional i32 oauthAuthServerPort

  30: optional bool loadBalancerEnabled
}

enum DeployResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
  NO_MANAGEMENT_HOST = 3
  EXIST_RUNNING_DEPLOYMENT = 4
  INVALID_OAUTH_CONFIG = 5
}

struct DeployResult {
  1: required DeployResultCode code
  2: optional string error
}

struct DeployRequest {
  1:  required Deployment deployment
  2:  optional string desired_state
  99: optional tracing.TracingInfo tracing_info
}

struct DeployResponse {
  1: required DeployResult result
  2: optional string operation_id
}

enum DeployStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct DeployStageStatus {
  1: required string name
  2: optional DeployStatusCode code
}

struct DeployStatus {
  1: required DeployStatusCode code
  2: optional string error
  3: optional list<DeployStageStatus> stages
}

struct DeployStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct DeployStatusResponse {
  1: required DeployResult result
  2: optional DeployStatus status
}

// Remove deployment
struct RemoveDeploymentRequest {
  // The deployment to remove (this is the ID of the deployment)
  1: required string id
  99: optional tracing.TracingInfo tracing_info
}

enum RemoveDeploymentResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
}

struct RemoveDeploymentResult {
  1: required RemoveDeploymentResultCode code
  2: optional string error
}

struct RemoveDeploymentResponse {
  1: required RemoveDeploymentResult result
  // ID of the removal operation
  2: optional string operation_id
}

enum RemoveDeploymentStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct RemoveDeploymentStatus {
  1: required RemoveDeploymentStatusCode code
  2: optional string error
}

struct RemoveDeploymentStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct RemoveDeploymentStatusResponse {
  1: required RemoveDeploymentResult result
  2: optional RemoveDeploymentStatus status
}

// Initialize migrate deployment
struct InitializeMigrateDeploymentRequest {
  // The destination deployment to migrate into (this is the ID of the deployment)
  1: required string id
  2: required string source_deployment_address
  99: optional tracing.TracingInfo tracing_info
}

enum InitializeMigrateDeploymentResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
}

struct InitializeMigrateDeploymentResult {
  1: required InitializeMigrateDeploymentResultCode code
  2: optional string error
}

struct InitializeMigrateDeploymentResponse {
  1: required InitializeMigrateDeploymentResult result
  // ID of the operation
  2: optional string operation_id
}

enum InitializeMigrateDeploymentStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct InitializeMigrateDeploymentStatus {
  1: required InitializeMigrateDeploymentStatusCode code
  2: optional string error
}

struct InitializeMigrateDeploymentStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct InitializeMigrateDeploymentStatusResponse {
  1: required InitializeMigrateDeploymentResult result
  2: optional InitializeMigrateDeploymentStatus status
}

// Finalize migrate deployment
struct FinalizeMigrateDeploymentRequest {
  // The destination deployment to migrate into (this is the ID of the deployment)
  1: required string id
  2: required string source_deployment_address
  99: optional tracing.TracingInfo tracing_info
}

enum FinalizeMigrateDeploymentResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
}

struct FinalizeMigrateDeploymentResult {
  1: required FinalizeMigrateDeploymentResultCode code
  2: optional string error
}

struct FinalizeMigrateDeploymentResponse {
  1: required FinalizeMigrateDeploymentResult result
  // ID of the operation
  2: optional string operation_id
}

enum FinalizeMigrateDeploymentStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct FinalizeMigrateDeploymentStatus {
  1: required FinalizeMigrateDeploymentStatusCode code
  2: optional string error
}

struct FinalizeMigrateDeploymentStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct FinalizeMigrateDeploymentStatusResponse {
  1: required FinalizeMigrateDeploymentResult result
  2: optional FinalizeMigrateDeploymentStatus status
}
//////////////////////////////////////////////////////////////////////////////
// Set_Host_To_EnterMaintenanceMode
//////////////////////////////////////////////////////////////////////////////
struct EnterMaintenanceModeRequest {
  1: required string hostId
  99: optional tracing.TracingInfo tracing_info
}

enum EnterMaintenanceModeResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct EnterMaintenanceModeResult {
  1: required EnterMaintenanceModeResultCode code
  2: optional string error
}

struct EnterMaintenanceModeResponse {
  1: required EnterMaintenanceModeResult result
  2: optional string operation_id
}

enum EnterMaintenanceModeStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
}

struct EnterMaintenanceModeStatus {
  1: required EnterMaintenanceModeStatusCode code
  2: optional string error
}

struct EnterMaintenanceModeStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct EnterMaintenanceModeStatusResponse {
  1: required EnterMaintenanceModeResult result
  2: optional EnterMaintenanceModeStatus status
}

//////////////////////////////////////////////////////////////////////////////
// Set_Host_To_MaintenanceMode
//////////////////////////////////////////////////////////////////////////////
struct MaintenanceModeRequest {
  1: required string hostId
  99: optional tracing.TracingInfo tracing_info
}

enum MaintenanceModeResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct MaintenanceModeResult {
  1: required MaintenanceModeResultCode code
  2: optional string error
}

struct MaintenanceModeResponse {
  1: required MaintenanceModeResult result
  2: optional string operation_id
}

enum MaintenanceModeStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
}

struct MaintenanceModeStatus {
  1: required MaintenanceModeStatusCode code
  2: optional string error
}

struct MaintenanceModeStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct MaintenanceModeStatusResponse {
  1: required MaintenanceModeResult result
  2: optional MaintenanceModeStatus status
}

//////////////////////////////////////////////////////////////////////////////
// Set_Host_To_NormalMode
//////////////////////////////////////////////////////////////////////////////
struct NormalModeRequest {
  1: required string hostId
  99: optional tracing.TracingInfo tracing_info
}

enum NormalModeResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct NormalModeResult {
  1: required NormalModeResultCode code
  2: optional string error
}

struct NormalModeResponse {
  1: required NormalModeResult result
  2: optional string operation_id
}

enum NormalModeStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
}

struct NormalModeStatus {
  1: required NormalModeStatusCode code
  2: optional string error
}

struct NormalModeStatusRequest {
  // ID of of the deploy operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct NormalModeStatusResponse {
  1: required NormalModeResult result
  2: optional NormalModeStatus status
}

//////////////////////////////////////////////////////////////////////////////
// DeprovisionHosts
//////////////////////////////////////////////////////////////////////////////
struct DeprovisionHostRequest {
  1: required string host_id
  99: optional tracing.TracingInfo tracing_info
}

enum DeprovisionHostResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
}

struct DeprovisionHostResult {
  1: required DeprovisionHostResultCode code
  2: optional string error
}

struct DeprovisionHostResponse {
  1: required DeprovisionHostResult result
  2: optional string operation_id
}

enum DeprovisionHostStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct DeprovisionHostStatus {
  1: required DeprovisionHostStatusCode result
  2: optional string error
}

struct DeprovisionHostStatusRequest {
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct DeprovisionHostStatusResponse {
  1: required DeprovisionHostResult result
  2: optional DeprovisionHostStatus status
}

//////////////////////////////////////////////////////////////////////////////
// ProvisionHost
//////////////////////////////////////////////////////////////////////////////
struct ProvisionHostRequest {
  1: required string host_id
  99: optional tracing.TracingInfo tracing_info
}

enum ProvisionHostResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
}

struct ProvisionHostResult {
  1: required ProvisionHostResultCode code
  2: optional string error
}

struct ProvisionHostResponse {
  1: required ProvisionHostResult result
  2: optional string operation_id
}

enum ProvisionHostStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct ProvisionHostStatus {
  1: required ProvisionHostStatusCode result
  2: optional string error
}

struct ProvisionHostStatusRequest {
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct ProvisionHostStatusResponse {
  1: required ProvisionHostResult result
  2: optional ProvisionHostStatus status
}

// Deployer service
service Deployer {
  status.Status get_status();

  CreateHostResponse create_host(1: CreateHostRequest request)
  CreateHostStatusResponse create_host_status(1: CreateHostStatusRequest request)
  DeleteHostResponse delete_host(1: DeleteHostRequest request)

  DeployResponse deploy(1: DeployRequest request)
  DeployStatusResponse deploy_status(1: DeployStatusRequest request)

  DeprovisionHostResponse deprovision_host(1: DeprovisionHostRequest request)
  DeprovisionHostStatusResponse deprovision_host_status(1: DeprovisionHostStatusRequest request)

  EnterMaintenanceModeResponse set_host_to_enter_maintenance_mode(1: EnterMaintenanceModeRequest request)
  EnterMaintenanceModeStatusResponse set_host_to_enter_maintenance_mode_status(1: EnterMaintenanceModeStatusRequest request)

  MaintenanceModeResponse set_host_to_maintenance_mode(1: MaintenanceModeRequest request)
  MaintenanceModeStatusResponse set_host_to_maintenance_mode_status(1: MaintenanceModeStatusRequest request)

  NormalModeResponse set_host_to_normal_mode(1: NormalModeRequest request)
  NormalModeStatusResponse set_host_to_normal_mode_status(1: NormalModeStatusRequest request)

  ProvisionHostResponse provision_host(1: ProvisionHostRequest request)
  ProvisionHostStatusResponse provision_host_status(1: ProvisionHostStatusRequest request)

  RemoveDeploymentResponse remove_deployment(1: RemoveDeploymentRequest request)
  RemoveDeploymentStatusResponse remove_deployment_status(1: RemoveDeploymentStatusRequest request)

  InitializeMigrateDeploymentResponse initialize_migrate_deployment(1: InitializeMigrateDeploymentRequest request)
  InitializeMigrateDeploymentStatusResponse initialize_migrate_deployment_status(1: InitializeMigrateDeploymentStatusRequest request)
  FinalizeMigrateDeploymentResponse finalize_migrate_deployment(1: FinalizeMigrateDeploymentRequest request)
  FinalizeMigrateDeploymentStatusResponse finalize_migrate_deployment_status(1: FinalizeMigrateDeploymentStatusRequest request)
}
