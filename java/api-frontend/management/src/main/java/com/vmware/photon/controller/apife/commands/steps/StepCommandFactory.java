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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.apife.backends.AttachedDiskBackend;
import com.vmware.photon.controller.apife.backends.ClusterBackend;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.ImageStoreFactory;
import com.vmware.photon.controller.apife.lib.VsphereIsoStore;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory Class that creates StepCommand objects.
 */
@Singleton
public class StepCommandFactory {

  private final StepBackend stepBackend;
  private final EntityLockBackend entityLockBackend;
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private final AttachedDiskBackend attachedDiskBackend;
  private final ImageBackend imageBackend;
  private final TaskBackend taskBackend;
  private final DeploymentBackend deploymentBackend;
  private final HostBackend hostBackend;
  private final ImageConfig imageConfig;
  private final ImageStoreFactory imageStoreFactory;
  private final ServiceConfig serviceConfig;
  private final VsphereIsoStore isoStore;
  private final FlavorBackend flavorBackend;
  private final ClusterBackend clusterBackend;
  private final NetworkBackend networkBackend;
  private final TenantBackend tenantBackend;
  private final ProjectBackend projectBackend;
  private final Boolean useVirtualNetwork;

  @Inject
  public StepCommandFactory(StepBackend stepBackend,
                            EntityLockBackend entityLockBackend,
                            VmBackend vmBackend,
                            DiskBackend diskBackend,
                            AttachedDiskBackend attachedDiskBackend,
                            ImageBackend imageBackend,
                            TaskBackend taskBackend,
                            DeploymentBackend deploymentBackend,
                            HostBackend hostBackend,
                            ImageConfig imageConfig,
                            ImageStoreFactory imageStoreFactory,
                            ServiceConfig serviceConfig,
                            VsphereIsoStore isoStore,
                            NetworkBackend networkBackend,
                            FlavorBackend flavorBackend,
                            ClusterBackend clusterBackend,
                            TenantBackend tenantBackend,
                            ProjectBackend projectBackend,
                            @Named("useVirtualNetwork") Boolean useVirtualNetwork) {
    this.stepBackend = stepBackend;
    this.entityLockBackend = entityLockBackend;
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
    this.attachedDiskBackend = attachedDiskBackend;
    this.imageBackend = imageBackend;
    this.taskBackend = taskBackend;
    this.deploymentBackend = deploymentBackend;
    this.hostBackend = hostBackend;
    this.imageConfig = imageConfig;
    this.serviceConfig = serviceConfig;
    this.imageStoreFactory = imageStoreFactory;
    this.isoStore = isoStore;
    this.networkBackend = networkBackend;
    this.flavorBackend = flavorBackend;
    this.clusterBackend = clusterBackend;
    this.tenantBackend = tenantBackend;
    this.projectBackend = projectBackend;
    this.useVirtualNetwork = useVirtualNetwork;
  }

  public StepCommand createCommand(TaskCommand taskCommand, StepEntity stepEntity) throws InternalException {
    checkNotNull(stepEntity);
    switch (stepEntity.getOperation()) {
      case RESERVE_RESOURCE:
        return new ResourceReserveStepCmd(taskCommand, stepBackend, stepEntity, diskBackend, vmBackend,
            networkBackend, flavorBackend, useVirtualNetwork);
      case CREATE_DISK:
        return new DiskCreateStepCmd(taskCommand, stepBackend, stepEntity, diskBackend);
      case DELETE_DISK:
        return new DiskDeleteStepCmd(taskCommand, stepBackend, stepEntity, diskBackend, attachedDiskBackend);
      case CREATE_VM:
        return new VmCreateStepCmd(taskCommand, stepBackend, stepEntity, vmBackend, diskBackend);
      case DELETE_VM:
        return new VmDeleteStepCmd(taskCommand, stepBackend, stepEntity, vmBackend, diskBackend);
      case START_VM:
      case STOP_VM:
      case RESTART_VM:
      case SUSPEND_VM:
      case RESUME_VM:
        return new VmPowerOpStepCmd(taskCommand, stepBackend, stepEntity, vmBackend);
      case ATTACH_DISK:
      case DETACH_DISK:
        return new VmDiskOpStepCmd(taskCommand, stepBackend, stepEntity, diskBackend, attachedDiskBackend);
      case ATTACH_ISO:
        return new IsoAttachStepCmd(taskCommand, stepBackend, stepEntity, vmBackend, entityLockBackend);
      case DETACH_ISO:
        return new IsoDetachStepCmd(taskCommand, stepBackend, stepEntity, vmBackend);
      case UPLOAD_ISO:
        return new IsoUploadStepCmd(taskCommand, stepBackend, stepEntity, vmBackend, isoStore);
      case GET_NETWORKS:
        return new VmGetNetworksStepCmd(taskCommand, stepBackend, stepEntity, taskBackend, networkBackend);
      case GET_MKS_TICKET:
        return new VmGetMksTicketStepCmd(taskCommand, stepBackend, stepEntity, taskBackend);
      case CREATE_VM_IMAGE:
        return new VmCreateImageStepCmd(taskCommand, stepBackend, stepEntity, imageBackend, imageStoreFactory.create());
      case UPLOAD_IMAGE:
        return new ImageUploadStepCmd(
            taskCommand, stepBackend, stepEntity, imageBackend, imageStoreFactory.create(), imageConfig);
      case REPLICATE_IMAGE:
        return new ImageReplicateStepCmd(
            taskCommand, stepBackend, stepEntity, imageBackend, imageStoreFactory.create());
      case DELETE_IMAGE:
        return new ImageDeleteStepCmd(taskCommand, stepBackend, stepEntity, imageBackend);
      case CREATE_HOST:
        return new HostCreateStepCmd(taskCommand, stepBackend, stepEntity, hostBackend);
      case PROVISION_HOST:
        return new HostProvisionStepCmd(taskCommand, stepBackend, stepEntity, hostBackend);
      case DEPROVISION_HOST:
        return new HostDeprovisionStepCmd(taskCommand, stepBackend, stepEntity, hostBackend);
      case DELETE_HOST:
        return new HostDeleteStepCmd(taskCommand, stepBackend, stepEntity, hostBackend, vmBackend);
      case SUSPEND_HOST:
        return new HostEnterSuspendedModeStepCmd(taskCommand, stepBackend, stepEntity, hostBackend);
      case RESUME_HOST:
        return new HostResumeStepCmd(taskCommand, stepBackend, stepEntity, hostBackend);
      case ENTER_MAINTENANCE_MODE:
        return new HostEnterMaintenanceModeStepCmd(taskCommand, stepBackend, stepEntity, hostBackend, vmBackend);
      case EXIT_MAINTENANCE_MODE:
        return new HostExitMaintenanceModeStepCmd(taskCommand, stepBackend, stepEntity, hostBackend);
      case SET_AVAILABILITYZONE:
        return new HostSetAvailabilityZoneStepCmd(taskCommand, stepBackend, stepEntity, hostBackend);
      case SCHEDULE_DEPLOYMENT:
        return new DeploymentCreateStepCmd(taskCommand, stepBackend, stepEntity, deploymentBackend);
      case SCHEDULE_INITIALIZE_MIGRATE_DEPLOYMENT:
        return new DeploymentInitializeMigrationStepCmd(taskCommand, stepBackend, stepEntity, deploymentBackend);
      case PERFORM_INITIALIZE_MIGRATE_DEPLOYMENT:
        return new DeploymentInitializeMigrationStatusStepCmd(taskCommand, stepBackend, stepEntity);
      case SCHEDULE_FINALIZE_MIGRATE_DEPLOYMENT:
        return new DeploymentFinalizeMigrationStepCmd(taskCommand, stepBackend, stepEntity, deploymentBackend);
      case PERFORM_FINALIZE_MIGRATE_DEPLOYMENT:
        return new DeploymentFinalizeMigrationStatusStepCmd(taskCommand, stepBackend, stepEntity);
      case PROVISION_CONTROL_PLANE_HOSTS:
      case PROVISION_CONTROL_PLANE_VMS:
      case PROVISION_CLOUD_HOSTS:
      case PROVISION_CLUSTER_MANAGER:
      case MIGRATE_DEPLOYMENT_DATA:
        return new DeploymentStatusStepCmd(taskCommand, stepBackend, stepEntity, deploymentBackend);
      case SCHEDULE_DELETE_DEPLOYMENT:
        return new DeploymentDeleteStepCmd(taskCommand, stepBackend, stepEntity, deploymentBackend);
      case PERFORM_DELETE_DEPLOYMENT:
        return new DeploymentDeleteStatusStepCmd(taskCommand, stepBackend, stepEntity, deploymentBackend);
      case PUSH_DEPLOYMENT_SECURITY_GROUPS:
        return new DeploymentPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
      case CREATE_KUBERNETES_CLUSTER_INITIATE:
        return new KubernetesClusterCreateStepCmd(taskCommand, stepBackend, stepEntity, clusterBackend);
      case CREATE_KUBERNETES_CLUSTER_SETUP_ETCD:
      case CREATE_KUBERNETES_CLUSTER_SETUP_MASTER:
      case CREATE_KUBERNETES_CLUSTER_SETUP_SLAVES:
        return new ClusterTaskStatusStepCmd(taskCommand, stepBackend, stepEntity,
            new KubernetesClusterCreateTaskStatusPoller(taskCommand, clusterBackend, taskBackend));
      case CREATE_MESOS_CLUSTER_INITIATE:
        return new MesosClusterCreateStepCmd(taskCommand, stepBackend, stepEntity, clusterBackend);
      case CREATE_MESOS_CLUSTER_SETUP_ZOOKEEPERS:
      case CREATE_MESOS_CLUSTER_SETUP_MASTERS:
      case CREATE_MESOS_CLUSTER_SETUP_MARATHON:
      case CREATE_MESOS_CLUSTER_SETUP_SLAVES:
        return new ClusterTaskStatusStepCmd(taskCommand, stepBackend, stepEntity,
            new MesosClusterCreateTaskStatusPoller(taskCommand, clusterBackend, taskBackend));
      case CREATE_SWARM_CLUSTER_INITIATE:
        return new SwarmClusterCreateStepCmd(taskCommand, stepBackend, stepEntity, clusterBackend);
      case CREATE_SWARM_CLUSTER_SETUP_ETCD:
      case CREATE_SWARM_CLUSTER_SETUP_MASTER:
      case CREATE_SWARM_CLUSTER_SETUP_SLAVES:
        return new ClusterTaskStatusStepCmd(taskCommand, stepBackend, stepEntity,
            new SwarmClusterCreateTaskStatusPoller(taskCommand, clusterBackend, taskBackend));
      case RESIZE_CLUSTER_INITIATE:
        return new ClusterResizeStepCmd(taskCommand, stepBackend, stepEntity, clusterBackend);
      case RESIZE_CLUSTER_INITIALIZE_CLUSTER:
      case RESIZE_CLUSTER_RESIZE:
        return new ClusterTaskStatusStepCmd(taskCommand, stepBackend, stepEntity,
            new ClusterResizeTaskStatusPoller(clusterBackend));
      case DELETE_CLUSTER_INITIATE:
        return new ClusterDeleteStepCmd(taskCommand, stepBackend, stepEntity, clusterBackend);
      case DELETE_CLUSTER_UPDATE_CLUSTER_DOCUMENT:
      case DELETE_CLUSTER_DELETE_VMS:
      case DELETE_CLUSTER_DOCUMENT:
        return new ClusterTaskStatusStepCmd(taskCommand, stepBackend, stepEntity,
            new ClusterDeleteTaskStatusPoller(clusterBackend));
      case SET_TENANT_SECURITY_GROUPS:
        return new TenantSetSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
      case PUSH_TENANT_SECURITY_GROUPS:
        return new TenantPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity,
            tenantBackend, projectBackend);
      case PAUSE_SYSTEM:
        return new SystemPauseStepCmd(taskCommand, stepBackend, stepEntity, serviceConfig);
      case PAUSE_BACKGROUND_TASKS:
        return new SystemPauseBackgroundTasksStepCmd(taskCommand, stepBackend, stepEntity, serviceConfig);
      case RESUME_SYSTEM:
        return new SystemResumeStepCmd(taskCommand, stepBackend, stepEntity, serviceConfig);
      default:
        throw new InternalException(String.format("Invalid Operation %s to create StepCommand",
            stepEntity.getOperation()));
    }
  }
}
