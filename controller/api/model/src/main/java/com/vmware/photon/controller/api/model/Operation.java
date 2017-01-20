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

package com.vmware.photon.controller.api.model;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Possible system operations.
 */
public enum Operation {
  ADD_TAG("AddTag"),
  ATTACH_DISK("AttachDisk"),
  DETACH_DISK("DetachDisk"),
  CREATE_DISK("CreateDisk"),
  DELETE_DISK("DeleteDisk"),
  SNAPSHOT_DISK("SnapshotDisk"),
  CREATE_PROJECT("CreateProject"),
  SET_PROJECT_SECURITY_GROUPS("SetProjectSecurityGroups"),
  CREATE_RESOURCE_TICKET("CreateResourceTicket"),
  CREATE_TENANT("CreateTenant"),
  DELETE_TENANT("DeleteTenant"),
  SET_TENANT_SECURITY_GROUPS("SetTenantSecurityGroups"),
  PUSH_TENANT_SECURITY_GROUPS("PushTenantSecurityGroups"),
  DELETE_PROJECT("DeleteProject"),

  RESERVE_RESOURCE("ReserveResource"),
  CREATE_VM("CreateVm"),
  STOP_VM("StopVm"),
  START_VM("StartVm"),
  RESTART_VM("RestartVm"),
  SUSPEND_VM("SuspendVm"),
  RESUME_VM("ResumeVm"),
  DELETE_VM("DeleteVm"),
  GET_MKS_TICKET("GetVmMksTicket"),
  GET_NETWORKS("GetVmNetworks"),
  SET_METADATA("SetMetadata"),
  CREATE_VM_IMAGE("CreateVmImage"),
  GET_VM_IP("GetVmIp"),
  RELEASE_VM_IP("ReleaseVmIp"),

  CREATE_IMAGE("CreateImage"),
  UPLOAD_IMAGE("UploadImage"),
  REPLICATE_IMAGE("ReplicateImage"),
  QUERY_REPLICATE_IMAGE_TASK_RESULT("QueryReplicateImage"),
  DELETE_IMAGE("DeleteImage"),

  ATTACH_ISO("AttachIso"),
  DETACH_ISO("DetachIso"),
  UPLOAD_ISO("UploadIso"),

  CREATE_FLAVOR("CreateFlavor"),
  DELETE_FLAVOR("DeleteFlavor"),

  CREATE_AVAILABILITYZONE("CreateAvailabilityZone"),
  DELETE_AVAILABILITYZONE("DeleteAvailabilityZone"),
  SET_AVAILABILITYZONE("SetAvailabilityZone"),

  CREATE_DATASTORE("CreateDatastore"),
  DELETE_DATASTORE("DeleteDatastore"),
  CREATE_NETWORK("CreateNetwork"),
  CREATE_VIRTUAL_NETWORK("CreateVirtualNetwork"),
  DELETE_NETWORK("DeleteNetwork"),
  SET_PORT_GROUPS("SetPortGroups"),
  SET_DEFAULT_NETWORK("SetDefaultNetwork"),
  CREATE_PORT_GROUP("CreatePortGroup"),
  DELETE_PORT_GROUP("DeletePortGroup"),
  CREATE_CONFIGURATION("CreateConfiguration"),
  DELETE_CONFIGURATION("DeleteConfiguration"),

  CREATE_HOST("CreateHost"),
  PROVISION_HOST("ProvisionHost"),
  QUERY_PROVISION_HOST_TASK_RESULT("QueryProvisionHostResult"),
  DELETE_HOST("DeleteHost"),
  DEPROVISION_HOST("DeprovisionHost"),
  QUERY_DEPROVISION_HOST_TASK_RESULT("QueryDeprovisionHostResult"),
  SUSPEND_HOST("EnterHostSuspendedMode"),
  ENTER_MAINTENANCE_MODE("EnterHostMaintenanceMode"),
  EXIT_MAINTENANCE_MODE("ExitHostMaintenanceMode"),
  RESUME_HOST("ResumeHost"),
  QUERY_HOST_CHANGE_MODE_TASK_RESULT("QueryHostChangeModeTaskResult"),
  QUERY_HOST_TASK_RESULT("QueryHostResult"),

  CREATE_DEPLOYMENT("CreateDeployment"),
  PERFORM_DEPLOYMENT("PerformDeployment"),
  PREPARE_DEPLOYMENT("PrepareDeployment"),
  SCHEDULE_DEPLOYMENT("ScheduleDeployment"),
  INITIALIZE_MIGRATE_DEPLOYMENT("InitializeMigrateDeployment"),
  SCHEDULE_INITIALIZE_MIGRATE_DEPLOYMENT("ScheduleInitializeMigrateDeployment"),
  PERFORM_INITIALIZE_MIGRATE_DEPLOYMENT("PerformInitializeMigrateDeployment"),
  FINALIZE_MIGRATE_DEPLOYMENT("FinalizeMigrateDeployment"),
  SCHEDULE_FINALIZE_MIGRATE_DEPLOYMENT("ScheduleFinalizeMigrateDeployment"),
  PERFORM_FINALIZE_MIGRATE_DEPLOYMENT("PerformFinalizeMigrateDeployment"),
  PROVISION_CONTROL_PLANE_HOSTS("ProvisionControlPlaneHosts"),
  PROVISION_CONTROL_PLANE_VMS("ProvisionControlPlaneVms"),
  PROVISION_CLOUD_HOSTS("ProvisionCloudHosts"),
  PROVISION_SERVICES_MANAGER("ProvisionServicesManager"),
  MIGRATE_DEPLOYMENT_DATA("MigrateDeploymentData"),
  DELETE_DEPLOYMENT("DeleteDeployment"),
  DESTROY_DEPLOYMENT("DestroyDeployment"),
  SCHEDULE_DELETE_DEPLOYMENT("ScheduleDeleteDeployment"),
  PERFORM_DELETE_DEPLOYMENT("PerformDeleteDeployment"),
  DEPROVISION_HOSTS("DeprovisionHosts"),
  UPDATE_DEPLOYMENT_SECURITY_GROUPS("UpdateDeploymentSecurityGroups"),
  PUSH_DEPLOYMENT_SECURITY_GROUPS("PushDeploymentSecurityGroups"),
  PAUSE_SYSTEM("PauseSystem"),
  PAUSE_BACKGROUND_TASKS("PauseBackgroundTasks"),
  RESUME_SYSTEM("ResumeSystem"),
  CONFIGURE_SERVICE("ConfigureService"),
  DELETE_SERVICE_CONFIGURATION("DeleteServiceConfiguration"),
  UPDATE_IMAGE_DATASTORES("UpdateImageDatastores"),
  SYNC_HOSTS_CONFIG_INITIATE("SyncHostsConfigInitiate"),
  SYNC_HOSTS_CONFIG("SyncHostsConfig"),

  IMPORT_DC_CONFIG("ImportDcConfig"),

  UNTAR_IMAGE("UntarImage"),

  SELECT_DEPLOYMENT_FLAVOR("SelectDeploymentFlavor"),
  OVERRIDE_DEPLOYMENT_FLAVOR("OverrideDeploymentFlavor"),

  GENERATE_MANIFEST("GenerateManifest"),

  SCHEDULE_DEPLOY_AGENT_TASKS("ScheduleDeployAgentTasks"),
  SCHEDULE_UPLOAD_IMAGE_TASKS("ScheduleUploadImageTasks"),
  SCHEDULE_DEPLOY_MANAGEMENT_VM_TASKS("ScheduleDeployManagementVmTasks"),
  SCHEDULE_DELETE_MANAGEMENT_VM_TASKS("ScheduleDeleteManagementVmTasks"),
  SCHEDULE_DELETE_AGENT_TASKS("ScheduleDeleteAgentTasks"),

  DEPLOY_AGENT("DeployAgent"),
  DEPLOY_MANAGEMENT_VM("DeployManagementVm"),

  DELETE_AGENT("DeleteAgent"),
  DELETE_MANAGEMENT_VM("DeleteManagementVm"),
  DELETE_MANAGEMENT_VM_STEMCELL("DeleteManagementVmStemcell"),

  UPDATE_ZK_REGISTRATION("UpdateZKRegistration"),
  UPDATE_DEPLOYMENT_ID("UpdateDeploymentId"),

  CREATE_SERVICE("CreateService"),
  CREATE_KUBERNETES_SERVICE_INITIATE("CreateKubernetesServiceInitiate"),
  CREATE_KUBERNETES_SERVICE_SETUP_ETCD("CreateKubernetesServiceSetupEtcd"),
  CREATE_KUBERNETES_SERVICE_SETUP_MASTER("CreateKubernetesServiceSetupMaster"),
  CREATE_KUBERNETES_SERVICE_UPDATE_EXTENDED_PROPERTIES("UpdateKubernetesExtendedProperties"),
  CREATE_KUBERNETES_SERVICE_SETUP_WORKERS("CreateKubernetesServiceSetupWorkers"),
  CREATE_MESOS_SERVICE_INITIATE("CreateMesosServiceInitiate"),
  CREATE_MESOS_SERVICE_SETUP_ZOOKEEPERS("CreateMesosServiceSetupZookeeper"),
  CREATE_MESOS_SERVICE_SETUP_MASTERS("CreateMesosServiceSetupMasters"),
  CREATE_MESOS_SERVICE_SETUP_MARATHON("CreateMesosServiceSetupMarathon"),
  CREATE_MESOS_SERVICE_SETUP_WORKERS("CreateMesosServiceSetupWorkers"),
  CREATE_SWARM_SERVICE_INITIATE("CreateSwarmServiceInitiate"),
  CREATE_SWARM_SERVICE_SETUP_ETCD("CreateSwarmServiceSetupEtcd"),
  CREATE_SWARM_SERVICE_SETUP_MASTER("CreateSwarmServiceSetupMaster"),
  CREATE_SWARM_SERVICE_SETUP_WORKERS("CreateSwarmServiceSetupWorkers"),
  CREATE_HARBOR_SERVICE_INITIATE("CreateHarborServiceInitiate"),
  CREATE_HARBOR_SERVICE_SETUP_HARBOR("CreateHarborServiceSetupHarbor"),
  CREATE_HARBOR_SERVICE_UPDATE_EXTENDED_PROPERTIES("CreateHarborServiceUpdateExtendedProperties"),
  RESIZE_SERVICE("ResizeService"),
  RESIZE_SERVICE_INITIATE("ResizeServiceInitiate"),
  RESIZE_SERVICE_INITIALIZE_SERVICE("ResizeServiceInitializeService"),
  RESIZE_SERVICE_RESIZE("ResizeServiceResize"),
  DELETE_SERVICE("DeleteService"),
  DELETE_SERVICE_INITIATE("DeleteServiceInitiate"),
  DELETE_SERVICE_UPDATE_SERVICE_DOCUMENT("DeleteServiceUpdateServiceDocument"),
  DELETE_SERVICE_DELETE_VMS("DeleteServiceDeleteVms"),
  DELETE_SERVICE_DOCUMENT("DeleteServiceDocument"),
  TRIGGER_SERVICE_MAINTENANCE("TriggerServiceMaintenance"),

  GET_NSX_CONFIGURATION("GetNsxConfiguration"),
  CREATE_LOGICAL_SWITCH("CreateLogicalSwitch"),
  CREATE_LOGICAL_ROUTER("CreateLogicalRouter"),
  SET_UP_LOGICAL_ROUTER("SetupLogicalRouter"),
  CREATE_SUBNET_ALLOCATOR("CreateSubnetAllocator"),
  CREATE_DHCP_SUBNET("CreateDhcpSubnet"),
  CONFIGURE_DHCP_RELAY_PROFILE("ConfigureDhcpRelayProfile"),
  CONFIGURE_DHCP_RELAY_SERVICE("ConfigureDhcpRelayService"),

  WAIT_FOR_TASKS("WaitForTasks"),

  // For testing ONLY
  MOCK_OP("MockOperation");

  private final String operation;

  private Operation(String operation) {
    this.operation = checkNotNull(operation);
  }

  public static Operation parseOperation(String text) {
    for (Operation o : Operation.values()) {
      if (o.getOperation().equalsIgnoreCase(text) ||
          o.toString().equalsIgnoreCase(text)) {
        return o;
      }
    }

    return null;
  }

  public String getOperation() {
    return operation;
  }
}
