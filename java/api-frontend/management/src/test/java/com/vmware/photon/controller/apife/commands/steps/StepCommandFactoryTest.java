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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.DcpBackendTestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.CommandTestModule;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandTest;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;

import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link StepCommandFactory}.
 */
@Guice(modules = {DcpBackendTestModule.class, TestModule.class, CommandTestModule.class})
public class StepCommandFactoryTest {

  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;

  @Inject
  private StepCommandFactory stepCommandFactory;

  private TaskCommand taskCommand;

  private StepEntity step;

  @Inject
  private BasicServiceHost basicServiceHost;

  @Inject
  private ApiFeDcpRestClient apiFeDcpRestClient;

  @AfterClass
  public static void afterClassCleanup() throws Throwable {
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  @BeforeMethod
  public void setUp() throws Exception {
    host = basicServiceHost;
    dcpClient = apiFeDcpRestClient;

    step = new StepEntity();
    step.setId("Step ID");
    this.taskCommand = new TaskCommandTest().testTaskCommand;
  }

  /**
   * Test that command classes are created correctly.
   * @param operation
   * @param commandClass
   * @throws InternalException
   */
  @Test(dataProvider = "CreateCommand")
  public void testCreateCommand(Operation operation, Class commandClass) throws InternalException {
    step.setOperation(operation);
    StepCommand stepCommand = stepCommandFactory.createCommand(taskCommand, step);
    assertThat(stepCommand.getClass().isAssignableFrom(commandClass), is(true));
  }

  @DataProvider(name = "CreateCommand")
  public Object[][] getCreateCommandParams() {
    return new Object[][] {
        // Disk & VM operations
        {Operation.RESERVE_RESOURCE, ResourceReserveStepCmd.class},

        // Disk
        {Operation.CREATE_DISK, DiskCreateStepCmd.class},
        {Operation.DELETE_DISK, DiskDeleteStepCmd.class},

        // VM
        {Operation.CREATE_VM, VmCreateStepCmd.class},
        {Operation.DELETE_VM, VmDeleteStepCmd.class},
        {Operation.START_VM, VmPowerOpStepCmd.class},
        {Operation.STOP_VM, VmPowerOpStepCmd.class},
        {Operation.RESTART_VM, VmPowerOpStepCmd.class},
        {Operation.SUSPEND_VM, VmPowerOpStepCmd.class},
        {Operation.RESUME_VM, VmPowerOpStepCmd.class},

        {Operation.ATTACH_DISK, VmDiskOpStepCmd.class},
        {Operation.DETACH_DISK, VmDiskOpStepCmd.class},

        {Operation.ATTACH_ISO, IsoAttachStepCmd.class},
        {Operation.DETACH_ISO, IsoDetachStepCmd.class},
        {Operation.UPLOAD_ISO, IsoUploadStepCmd.class},

        {Operation.GET_NETWORKS, VmGetNetworksStepCmd.class},
        {Operation.GET_MKS_TICKET, VmGetMksTicketStepCmd.class},
        {Operation.CREATE_VM_IMAGE, VmCreateImageStepCmd.class},

        // Image
        {Operation.UPLOAD_IMAGE, ImageUploadStepCmd.class},
        {Operation.REPLICATE_IMAGE, ImageReplicateStepCmd.class},
        {Operation.DELETE_IMAGE, ImageDeleteStepCmd.class},
        {Operation.DELETE_IMAGE_REPLICAS, ImageReplicasDeleteStepCmd.class},
        {Operation.IMAGE_SEEDING_PROGRESS_CHECK, ImageSeedingProgressCheckStepCmd.class},

        // Host
        {Operation.CREATE_HOST, HostCreateStepCmd.class},
        {Operation.PROVISION_HOST, HostProvisionStepCmd.class},
        {Operation.DEPROVISION_HOST, HostDeprovisionStepCmd.class},
        {Operation.DELETE_HOST, HostDeleteStepCmd.class},
        {Operation.SUSPEND_HOST, HostEnterSuspendedModeStepCmd.class},
        {Operation.RESUME_HOST, HostResumeStepCmd.class},
        {Operation.ENTER_MAINTENANCE_MODE, HostEnterMaintenanceModeStepCmd.class},
        {Operation.EXIT_MAINTENANCE_MODE, HostExitMaintenanceModeStepCmd.class},
        {Operation.SET_AVAILABILITYZONE, HostSetAvailabilityZoneStepCmd.class},

        // Deployment
        {Operation.PREPARE_DEPLOYMENT, DeploymentImageConfigUpdateStepCmd.class},
        {Operation.SCHEDULE_DEPLOYMENT, DeploymentCreateStepCmd.class},
        {Operation.SCHEDULE_INITIALIZE_MIGRATE_DEPLOYMENT, DeploymentInitializeMigrationStepCmd.class},
        {Operation.PERFORM_INITIALIZE_MIGRATE_DEPLOYMENT, DeploymentInitializeMigrationStatusStepCmd.class},
        {Operation.SCHEDULE_FINALIZE_MIGRATE_DEPLOYMENT, DeploymentFinalizeMigrationStepCmd.class},
        {Operation.PERFORM_FINALIZE_MIGRATE_DEPLOYMENT, DeploymentFinalizeMigrationStatusStepCmd.class},
        {Operation.PROVISION_CONTROL_PLANE_HOSTS, DeploymentStatusStepCmd.class},
        {Operation.PROVISION_CONTROL_PLANE_VMS, DeploymentStatusStepCmd.class},
        {Operation.PROVISION_CLOUD_HOSTS, DeploymentStatusStepCmd.class},
        {Operation.PROVISION_CLUSTER_MANAGER, DeploymentStatusStepCmd.class},
        {Operation.MIGRATE_DEPLOYMENT_DATA, DeploymentStatusStepCmd.class},
        {Operation.SCHEDULE_DELETE_DEPLOYMENT, DeploymentDeleteStepCmd.class},
        {Operation.PERFORM_DELETE_DEPLOYMENT, DeploymentDeleteStatusStepCmd.class},
        {Operation.PUSH_DEPLOYMENT_SECURITY_GROUPS, DeploymentPushSecurityGroupsStepCmd.class},

        // Cluster
        {Operation.CREATE_KUBERNETES_CLUSTER_INITIATE, KubernetesClusterCreateStepCmd.class},
        {Operation.CREATE_KUBERNETES_CLUSTER_SETUP_ETCD, ClusterTaskStatusStepCmd.class},
        {Operation.CREATE_KUBERNETES_CLUSTER_SETUP_MASTER, ClusterTaskStatusStepCmd.class},
        {Operation.CREATE_KUBERNETES_CLUSTER_SETUP_SLAVES, ClusterTaskStatusStepCmd.class},

        {Operation.CREATE_MESOS_CLUSTER_INITIATE, MesosClusterCreateStepCmd.class},
        {Operation.CREATE_MESOS_CLUSTER_SETUP_ZOOKEEPERS, ClusterTaskStatusStepCmd.class},
        {Operation.CREATE_MESOS_CLUSTER_SETUP_MASTERS, ClusterTaskStatusStepCmd.class},
        {Operation.CREATE_MESOS_CLUSTER_SETUP_MARATHON, ClusterTaskStatusStepCmd.class},
        {Operation.CREATE_MESOS_CLUSTER_SETUP_SLAVES, ClusterTaskStatusStepCmd.class},

        {Operation.CREATE_SWARM_CLUSTER_INITIATE, SwarmClusterCreateStepCmd.class},
        {Operation.CREATE_SWARM_CLUSTER_SETUP_ETCD, ClusterTaskStatusStepCmd.class},
        {Operation.CREATE_SWARM_CLUSTER_SETUP_MASTER, ClusterTaskStatusStepCmd.class},
        {Operation.CREATE_SWARM_CLUSTER_SETUP_SLAVES, ClusterTaskStatusStepCmd.class},

        {Operation.RESIZE_CLUSTER_INITIATE, ClusterResizeStepCmd.class},
        {Operation.RESIZE_CLUSTER_INITIALIZE_CLUSTER, ClusterTaskStatusStepCmd.class},
        {Operation.RESIZE_CLUSTER_RESIZE, ClusterTaskStatusStepCmd.class},

        {Operation.DELETE_CLUSTER_INITIATE, ClusterDeleteStepCmd.class},
        {Operation.DELETE_CLUSTER_UPDATE_CLUSTER_DOCUMENT, ClusterTaskStatusStepCmd.class},
        {Operation.DELETE_CLUSTER_DELETE_VMS, ClusterTaskStatusStepCmd.class},
        {Operation.DELETE_CLUSTER_DOCUMENT, ClusterTaskStatusStepCmd.class},

        // Misc
        {Operation.SET_TENANT_SECURITY_GROUPS, TenantSetSecurityGroupsStepCmd.class},
        {Operation.PUSH_TENANT_SECURITY_GROUPS, TenantPushSecurityGroupsStepCmd.class},
        {Operation.PAUSE_SYSTEM, SystemPauseStepCmd.class},
        {Operation.RESUME_SYSTEM, SystemResumeStepCmd.class}
    };
  }

  @Test(expectedExceptions = InternalException.class)
  public void testCreateCommandFailure() throws InternalException {
    step.setOperation(Operation.CREATE_RESOURCE_TICKET);
    stepCommandFactory.createCommand(taskCommand, step);
  }
}
