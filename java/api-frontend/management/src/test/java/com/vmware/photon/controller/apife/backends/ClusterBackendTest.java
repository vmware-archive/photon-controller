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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.ClusterCreateSpec;
import com.vmware.photon.controller.api.ClusterResizeOperation;
import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.apife.backends.clients.ClusterManagerClient;
import com.vmware.photon.controller.apife.commands.CommandTestModule;
import com.vmware.photon.controller.apife.commands.steps.ClusterDeleteStepCmd;
import com.vmware.photon.controller.apife.commands.steps.ClusterResizeStepCmd;
import com.vmware.photon.controller.apife.commands.steps.KubernetesClusterCreateStepCmd;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ImageDao;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.mockito.Mock;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link ClusterBackend}.
 */
public class ClusterBackendTest {
  @Test
  private void dummy() {
  }

  private static Cluster buildCluster(String clusterId) {
    Cluster cluster = new Cluster();
    cluster.setId(clusterId);
    cluster.setName("clusterName");
    cluster.setType(ClusterType.KUBERNETES);
    cluster.setState(ClusterState.READY);
    cluster.setProjectId("projectId");
    cluster.setSlaveCount(2);
    cluster.setExtendedProperties(ImmutableMap.of(
        ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "12.12.12.12"));

    return cluster;
  }

  /**
   * Tests for the create method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class CreateClusterTest extends BaseDaoTest {
    @Inject
    private TaskDao taskDao;
    @Inject
    private TaskBackend taskBackend;
    @Inject
    private VmBackend vmBackend;
    @Mock
    private ClusterManagerClient clusterManagerClient;
    private ClusterCreateSpec createSpec;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmBackend);
    }

    private static ClusterCreateSpec buildCreateSpec(ClusterType clusterType) {
      ClusterCreateSpec createSpec = new ClusterCreateSpec();
      createSpec.setName("clusterName");
      createSpec.setType(clusterType);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setSlaveCount(50);
      createSpec.setExtendedProperties(ImmutableMap.of(
          ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16"));
      return createSpec;
    }

    @Test
    public void testKubernetesCluster() throws Throwable {
      createSpec = buildCreateSpec(ClusterType.KUBERNETES);
      TaskEntity taskEntity = clusterBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 5);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_INITIATE);
      ClusterCreateSpec createSpecActual = (ClusterCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // reload taskEntity from storage
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 5);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_ALLOCATE_RESOURCES);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_SETUP_ETCD);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_SETUP_MASTER);
      assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_SETUP_SLAVES);
    }

    @Test
    public void testMesosCluster() throws Throwable {
      createSpec = buildCreateSpec(ClusterType.MESOS);
      TaskEntity taskEntity = clusterBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 6);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_MESOS_CLUSTER_INITIATE);
      ClusterCreateSpec createSpecActual = (ClusterCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // reload taskEntity from storage
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 6);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_MESOS_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_MESOS_CLUSTER_ALLOCATE_RESOURCES);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_ZOOKEEPERS);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_MASTERS);
      assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_MARATHON);
      assertEquals(taskEntity.getSteps().get(5).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_SLAVES);
    }

    @Test
    public void testSwarmCluster() throws Throwable {
      createSpec = buildCreateSpec(ClusterType.SWARM);
      TaskEntity taskEntity = clusterBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 5);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_SWARM_CLUSTER_INITIATE);
      ClusterCreateSpec createSpecActual = (ClusterCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // reload taskEntity from storage
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 5);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_SWARM_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_SWARM_CLUSTER_ALLOCATE_RESOURCES);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_SWARM_CLUSTER_SETUP_ETCD);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_SWARM_CLUSTER_SETUP_MASTER);
      assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.CREATE_SWARM_CLUSTER_SETUP_SLAVES);
    }
  }

  /**
   * Tests for the delete method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class DeleteClusterTest extends BaseDaoTest {
    @Inject
    private TaskDao taskDao;
    @Inject
    private TaskBackend taskBackend;
    @Inject
    private VmBackend vmBackend;
    @Mock
    private ClusterManagerClient clusterManagerClient;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmBackend);
    }

    @Test
    public void testSuccess() throws Throwable {
      String clusterId = UUID.randomUUID().toString();

      Cluster cluster = buildCluster(clusterId);
      when(clusterManagerClient.getCluster(clusterId)).thenReturn(cluster);

      TaskEntity taskEntity = clusterBackend.delete(clusterId);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 4);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.DELETE_CLUSTER_INITIATE);
      String clusterIdToDelete = (String) initiateStepEntity
          .getTransientResource(ClusterDeleteStepCmd.CLUSTER_ID_RESOURCE_KEY);
      assertEquals(clusterIdToDelete, clusterId);

      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // reload taskEntity from storage
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 4);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.DELETE_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.DELETE_CLUSTER_UPDATE_CLUSTER_DOCUMENT);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.DELETE_CLUSTER_DELETE_VMS);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.DELETE_CLUSTER_DOCUMENT);
    }

    @Test(expectedExceptions = ClusterNotFoundException.class)
    public void testClusterNotFound() throws Throwable {
      String clusterId = UUID.randomUUID().toString();
      when(clusterManagerClient.getCluster(clusterId)).thenThrow(new ClusterNotFoundException(clusterId));
      clusterBackend.delete(clusterId);
    }
  }

  /**
   * Tests for the resize method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class ResizeClusterTest extends BaseDaoTest {
    @Inject
    private TaskDao taskDao;
    @Inject
    private TaskBackend taskBackend;
    @Inject
    private VmBackend vmBackend;
    @Mock
    private ClusterManagerClient clusterManagerClient;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmBackend);
    }

    @Test
    public void testSuccess() throws Throwable {
      String clusterId = UUID.randomUUID().toString();

      Cluster cluster = buildCluster(clusterId);
      when(clusterManagerClient.getCluster(clusterId)).thenReturn(cluster);

      ClusterResizeOperation resizeOperation = new ClusterResizeOperation();
      resizeOperation.setNewSlaveCount(10);

      TaskEntity taskEntity = clusterBackend.resize(clusterId, resizeOperation);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);

      String clusterIdToResize = (String) initiateStepEntity
          .getTransientResource(ClusterResizeStepCmd.CLUSTER_ID_RESOURCE_KEY);
      assertEquals(clusterIdToResize, clusterId);

      ClusterResizeOperation resizeOperationReturned = (ClusterResizeOperation) initiateStepEntity
          .getTransientResource(ClusterResizeStepCmd.RESIZE_OPERATION_RESOURCE_KEY);
      assertEquals(resizeOperationReturned, resizeOperation);


      // clear the session so that subsequent reads are fresh queries
      flushSession();

      // reload taskEntity from storage
      taskEntity = taskDao.findById(taskEntity.getId()).get();
      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 3);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.RESIZE_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.RESIZE_CLUSTER_INITIALIZE_CLUSTER);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.RESIZE_CLUSTER_RESIZE);
    }

    @Test(expectedExceptions = ClusterNotFoundException.class)
    public void testClusterNotFound() throws Throwable {
      String clusterId = UUID.randomUUID().toString();
      when(clusterManagerClient.getCluster(clusterId)).thenThrow(new ClusterNotFoundException(clusterId));

      ClusterResizeOperation resizeOperation = new ClusterResizeOperation();
      resizeOperation.setNewSlaveCount(10);

      clusterBackend.resize(clusterId, resizeOperation);
    }
  }

  /**
   * Tests for the find method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class, CommandTestModule.class})
  public static class FindTest extends BaseDaoTest {
    @Inject
    private TaskBackend taskBackend;
    @Inject
    private VmBackend vmBackend;
    @Mock
    private ClusterManagerClient clusterManagerClient;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmBackend);
    }

    @Test
    public void testSuccess() throws Throwable {
      when(clusterManagerClient.getClusters(any(String.class)))
          .thenReturn(Arrays.asList(buildCluster("clusterId")));

      List<Cluster> clusters = clusterBackend.find("projectId");
      assertEquals(clusters.size(), 1);

      Cluster cluster = clusters.iterator().next();
      assertEquals(cluster.getId(), "clusterId");
      assertEquals(cluster.getName(), "clusterName");
      assertEquals(cluster.getType(), ClusterType.KUBERNETES);
      assertEquals(cluster.getProjectId(), "projectId");
      assertEquals(cluster.getSlaveCount(), 2);
      assertEquals(cluster.getExtendedProperties().get(
          ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK), "12.12.12.12");
    }

    @Test
    public void testNoClustersFound() throws Throwable {
      List<Cluster> clusters = clusterBackend.find("projectId");
      assertEquals(clusters.size(), 0);
    }
  }

  /**
   * Tests for the findVms method.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class, CommandTestModule.class})
  public static class FindVmTest extends BaseDaoTest {

    ClusterBackend clusterBackend;
    @Inject
    private EntityFactory entityFactory;
    @Inject
    private VmSqlBackend vmSqlBackend;
    @Inject
    private TaskBackend taskBackend;
    @Inject
    private ImageDao imageDao;
    @Mock
    private ClusterManagerClient clusterManagerClient;

    private String projectId;
    private String clusterId = UUID.randomUUID().toString();
    private String imageId;

    @BeforeMethod()
    public void setUp() throws Throwable {
      super.setUp();

      QuotaLineItemEntity ticketLimit = new QuotaLineItemEntity("vm.cost", 100, QuotaUnit.COUNT);
      QuotaLineItemEntity projectLimit = new QuotaLineItemEntity("vm.cost", 10, QuotaUnit.COUNT);

      String tenantId = entityFactory.createTenant("vmware").getId();
      String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", ticketLimit);
      projectId = entityFactory.createProject(tenantId, ticketId, "staging", projectLimit);
      entityFactory.loadFlavors();

      ImageEntity image = new ImageEntity();
      image.setName("image-1");
      image.setState(ImageState.READY);
      image.setSize(1024L * 1024);
      image = imageDao.create(image);
      flushSession();
      imageId = image.getId();

      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmSqlBackend);
    }

    @Test
    public void testFindVms() throws Throwable {
      when(clusterManagerClient.getCluster(any(String.class))).thenReturn(buildCluster());

      String[] vmIds = createMockCluster(clusterId, 5);
      createMockCluster(UUID.randomUUID().toString(), 3);
      List<Vm> vms = clusterBackend.findVms(clusterId);
      assertEquals(vms.size(), vmIds.length);
      for (String vmId : vmIds) {
        boolean found = false;
        for (Vm vm : vms) {
          if (vm.getId().equals(vmId)) {
            vms.remove(vm);
            found = true;
            break;
          }
        }
        assertTrue(found);
      }
    }

    @Test
    public void testFindVmsNoMatch() throws Throwable {
      when(clusterManagerClient.getCluster(any(String.class))).thenReturn(buildCluster());
      List<Vm> vms = clusterBackend.findVms(clusterId);
      assertEquals(vms.size(), 0);
    }

    @Test(expectedExceptions = ClusterNotFoundException.class)
    public void testClusterNotFound() throws Throwable {
      String clusterId = UUID.randomUUID().toString();
      when(clusterManagerClient.getCluster(clusterId)).thenThrow(new ClusterNotFoundException(clusterId));
      clusterBackend.findVms(clusterId);
    }

    private String createVm(String clusterId) throws Exception {
      AttachedDiskCreateSpec disk1 =
          new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();

      VmCreateSpec spec = new VmCreateSpec();
      spec.setName("test-vm");
      spec.setFlavor("core-100");
      spec.setSourceImageId(imageId);
      spec.setAttachedDisks(ImmutableList.of(disk1));
      spec.setTags(ImmutableSet.of(ClusterUtil.createClusterTag(clusterId)));
      String vmId = vmSqlBackend.create(projectId, spec).getId();
      flushSession();
      return vmId;
    }

    private String[] createMockCluster(String id, int clusterSize) throws Exception {
      List<String> vmIds = new ArrayList<>();
      for (int i = 0; i < clusterSize; i++) {
        String vmId = createVm(id);
        vmIds.add(vmId);
      }
      return vmIds.toArray(new String[vmIds.size()]);
    }

    private Cluster buildCluster() {
      Cluster cluster = new Cluster();
      cluster.setProjectId(projectId);
      cluster.setName("clusterName");
      return cluster;
    }
  }
}
