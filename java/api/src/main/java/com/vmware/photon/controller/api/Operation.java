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

package com.vmware.photon.controller.api;

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

  CREATE_IMAGE("CreateImage"),
  UPLOAD_IMAGE("UploadImage"),
  REPLICATE_IMAGE("ReplicateImage"),
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
  CREATE_PORT_GROUP("CreatePortGroup"),
  DELETE_PORT_GROUP("DeletePortGroup"),
  CREATE_CONFIGURATION("CreateConfiguration"),
  DELETE_CONFIGURATION("DeleteConfiguration"),

  CREATE_HOST("CreateHost"),
  PROVISION_HOST("ProvisionHost"),
  DELETE_HOST("DeleteHost"),
  DEPROVISION_HOST("DeprovisionHost"),
  SUSPEND_HOST("EnterHostSuspendedMode"),
  ENTER_MAINTENANCE_MODE("EnterHostMaintenanceMode"),
  EXIT_MAINTENANCE_MODE("ExitHostMaintenanceMode"),
  RESUME_HOST("ResumeHost"),


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
  PROVISION_CLUSTER_MANAGER("ProvisionClusterManager"),
  MIGRATE_DEPLOYMENT_DATA("MigrateDeploymentData"),
  DELETE_DEPLOYMENT("DeleteDeployment"),
  DESTROY_DEPLOYMENT("DestroyDeployment"),
  SCHEDULE_DELETE_DEPLOYMENT("ScheduleDeleteDeployment"),
  PERFORM_DELETE_DEPLOYMENT("PerformDeleteDeployment"),
  UPDATE_DEPLOYMENT_SECURITY_GROUPS("UpdateDeploymentSecurityGroups"),
  PUSH_DEPLOYMENT_SECURITY_GROUPS("PushDeploymentSecurityGroups"),
  PAUSE_SYSTEM("PauseSystem"),
  PAUSE_BACKGROUND_TASKS("PauseBackgroundTasks"),
  RESUME_SYSTEM("ResumeSystem"),
  DELETE_CLUSTER_CONFIGURATION("DeleteClusterConfiguration"),
  UPDATE_IMAGE_DATASTORES("UpdateImageDatastores"),

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

  CREATE_CLUSTER("CreateCluster"),
  CREATE_KUBERNETES_CLUSTER_INITIATE("CreateKubernetesClusterInitiate"),
  CREATE_KUBERNETES_CLUSTER_SETUP_ETCD("CreateKubernetesClusterSetupEtcd"),
  CREATE_KUBERNETES_CLUSTER_SETUP_MASTER("CreateKubernetesClusterSetupMaster"),
  CREATE_KUBERNETES_CLUSTER_SETUP_SLAVES("CreateKubernetesClusterSetupSlaves"),
  CREATE_MESOS_CLUSTER_INITIATE("CreateMesosClusterInitiate"),
  CREATE_MESOS_CLUSTER_SETUP_ZOOKEEPERS("CreateMesosClusterSetupZookeeper"),
  CREATE_MESOS_CLUSTER_SETUP_MASTERS("CreateMesosClusterSetupMasters"),
  CREATE_MESOS_CLUSTER_SETUP_MARATHON("CreateMesosClusterSetupMarathon"),
  CREATE_MESOS_CLUSTER_SETUP_SLAVES("CreateMesosClusterSetupSlaves"),
  CREATE_SWARM_CLUSTER_INITIATE("CreateSwarmClusterInitiate"),
  CREATE_SWARM_CLUSTER_SETUP_ETCD("CreateSwarmClusterSetupEtcd"),
  CREATE_SWARM_CLUSTER_SETUP_MASTER("CreateSwarmClusterSetupMaster"),
  CREATE_SWARM_CLUSTER_SETUP_SLAVES("CreateSwarmClusterSetupSlaves"),
  RESIZE_CLUSTER("ResizeCluster"),
  RESIZE_CLUSTER_INITIATE("ResizeClusterInitiate"),
  RESIZE_CLUSTER_INITIALIZE_CLUSTER("ResizeClusterInitializeCluster"),
  RESIZE_CLUSTER_RESIZE("ResizeClusterResize"),
  DELETE_CLUSTER("DeleteCluster"),
  DELETE_CLUSTER_INITIATE("DeleteClusterInitiate"),
  DELETE_CLUSTER_UPDATE_CLUSTER_DOCUMENT("DeleteClusterUpdateClusterDocument"),
  DELETE_CLUSTER_DELETE_VMS("DeleteClusterDeleteVms"),
  DELETE_CLUSTER_DOCUMENT("DeleteClusterDocument"),

  GET_NSX_CONFIGURATION("GetNsxConfiguration"),
  CREATE_LOGICAL_SWITCH("CreateLogicalSwitch"),
  CREATE_LOGICAL_ROUTER("CreateLogicalRouter"),
  SET_UP_LOGICAL_ROUTER("SetupLogicalRouter"),

  WAIT_FOR_TASKS("WaitForTasks"),

  // For testing ONLY
  MOCK_OP("MockOperation");

  private final String operation;

  private Operation(String operation) {
    this.operation = checkNotNull(operation);
  }

  public static Operation parseOperation(String text) {
    for (Operation o : Operation.values()) {
      if (o.getOperation().equalsIgnoreCase(text)) {
        return o;
      }
    }

    return null;
  }

  public String getOperation() {
    return operation;
  }
}
