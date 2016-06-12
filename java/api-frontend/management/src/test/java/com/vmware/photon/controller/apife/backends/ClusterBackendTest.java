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
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.ClusterManagerClient;
import com.vmware.photon.controller.apife.commands.CommandTestModule;
import com.vmware.photon.controller.apife.commands.steps.ClusterDeleteStepCmd;
import com.vmware.photon.controller.apife.commands.steps.ClusterResizeStepCmd;
import com.vmware.photon.controller.apife.commands.steps.KubernetesClusterCreateStepCmd;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.commons.collections.CollectionUtils;
import org.junit.AfterClass;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tests {@link ClusterBackend}.
 */
public class ClusterBackendTest {
  private static ApiFeXenonRestClient dcpClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (dcpClient == null) {
      throw new IllegalStateException(
          "dcpClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }


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
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class CreateClusterTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    private ClusterManagerClient clusterManagerClient;
    private ClusterCreateSpec createSpec;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      clusterManagerClient = mock(ClusterManagerClient.class);
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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
      assertEquals(taskEntity.getSteps().size(), 4);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_INITIATE);
      ClusterCreateSpec createSpecActual = (ClusterCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 4);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_SETUP_ETCD);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_SETUP_MASTER);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_KUBERNETES_CLUSTER_SETUP_SLAVES);
    }

    @Test
    public void testMesosCluster() throws Throwable {
      createSpec = buildCreateSpec(ClusterType.MESOS);
      TaskEntity taskEntity = clusterBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 5);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_MESOS_CLUSTER_INITIATE);
      ClusterCreateSpec createSpecActual = (ClusterCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 5);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_MESOS_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_ZOOKEEPERS);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_MASTERS);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_MARATHON);
      assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.CREATE_MESOS_CLUSTER_SETUP_SLAVES);
    }

    @Test
    public void testSwarmCluster() throws Throwable {
      createSpec = buildCreateSpec(ClusterType.SWARM);
      TaskEntity taskEntity = clusterBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 4);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_SWARM_CLUSTER_INITIATE);
      ClusterCreateSpec createSpecActual = (ClusterCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 4);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_SWARM_CLUSTER_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_SWARM_CLUSTER_SETUP_ETCD);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_SWARM_CLUSTER_SETUP_MASTER);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_SWARM_CLUSTER_SETUP_SLAVES);
    }
  }

  /**
   * Tests for the delete method.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class DeleteClusterTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmDcpBackend vmDcpBackend;

    private ClusterManagerClient clusterManagerClient;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      clusterManagerClient = mock(ClusterManagerClient.class);
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmDcpBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class ResizeClusterTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmDcpBackend vmDcpBackend;

    private ClusterManagerClient clusterManagerClient;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      clusterManagerClient = mock(ClusterManagerClient.class);
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmDcpBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
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
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class, CommandTestModule.class})
  public static class FindTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    private ClusterManagerClient clusterManagerClient;
    private ClusterBackend clusterBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      clusterManagerClient = mock(ClusterManagerClient.class);
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testSuccess() throws Throwable {
      Cluster c1 = buildCluster("clusterId1");
      Cluster c2 = buildCluster("clusterId2");

      String projectId = "projectId";
      String nextPageLink = UUID.randomUUID().toString();
      when(clusterManagerClient.getClusters(projectId, Optional.of(1)))
          .thenReturn(new ResourceList<Cluster>(Arrays.asList(c1), nextPageLink, null));
      when(clusterManagerClient.getClustersPages(nextPageLink))
          .thenReturn(new ResourceList<Cluster>(Arrays.asList(c2)));

      ResourceList<Cluster> clusters = clusterBackend.find("projectId", Optional.of(1));
      assertEquals(clusters.getItems().size(), 1);
      assertEquals(clusters.getItems().get(0), c1);
      assertEquals(clusters.getNextPageLink(), nextPageLink);

      clusters = clusterBackend.getClustersPage(nextPageLink);
      assertEquals(clusters.getItems().size(), 1);
      assertEquals(clusters.getItems().get(0), c2);
    }

    @Test
    public void testNoClustersFound() throws Throwable {
      String projectId = "projectId";
      when(clusterManagerClient.getClusters(projectId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
          .thenReturn(new ResourceList<>(new ArrayList<>()));

      ResourceList<Cluster> clusters = clusterBackend.find("projectId",
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertEquals(clusters.getItems().size(), 0);
    }
  }

  /**
   * Tests for the findVms method.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class, CommandTestModule.class})
  public static class FindVmTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    private ClusterBackend clusterBackend;

    @Inject
    private VmDcpBackend vmDcpBackend;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private TenantDcpBackend tenantDcpBackend;

    @Inject
    private ResourceTicketDcpBackend resourceTicketDcpBackend;

    @Inject
    private ProjectDcpBackend projectDcpBackend;

    @Inject
    private FlavorDcpBackend flavorDcpBackend;

    @Inject
    private FlavorLoader flavorLoader;

    private ClusterManagerClient clusterManagerClient;

    private String projectId;
    private String clusterId = UUID.randomUUID().toString();
    private String imageId;

    @BeforeMethod()
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      String tenantId = DcpBackendTestHelper.createTenant(tenantDcpBackend, "vmware");

      QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
      DcpBackendTestHelper.createTenantResourceTicket(resourceTicketDcpBackend,
          tenantId, "rt1", ImmutableList.of(ticketLimit));

      QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
      projectId = DcpBackendTestHelper.createProject(projectDcpBackend,
          "staging", tenantId, "rt1", ImmutableList.of(projectLimit));

      DcpBackendTestHelper.createFlavors(flavorDcpBackend, flavorLoader.getAllFlavors());

      ImageService.State imageServiceState = new ImageService.State();
      imageServiceState.name = "image-1";
      imageServiceState.state = ImageState.READY;
      imageServiceState.size = 1024L * 1024L;
      imageServiceState.replicationType = ImageReplicationType.EAGER;
      imageServiceState.imageSettings = new ArrayList<>();
      ImageService.State.ImageSetting imageSetting = new ImageService.State.ImageSetting();
      imageSetting.name = "n1";
      imageSetting.defaultValue = "v1";
      imageServiceState.imageSettings.add(imageSetting);
      imageSetting = new ImageService.State.ImageSetting();
      imageSetting.name = "n2";
      imageSetting.defaultValue = "v2";
      imageServiceState.imageSettings.add(imageSetting);

      com.vmware.xenon.common.Operation result = dcpClient.post(ImageServiceFactory.SELF_LINK, imageServiceState);

      ImageService.State createdImageState = result.getBody(ImageService.State.class);

      imageId = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

      clusterManagerClient = mock(ClusterManagerClient.class);
      clusterBackend = new ClusterBackend(clusterManagerClient, taskBackend, vmDcpBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testFindVmsNoPagination() throws Throwable {
      when(clusterManagerClient.getCluster(any(String.class))).thenReturn(buildCluster());

      String[] vmIds = createMockCluster(clusterId, 5);
      createMockCluster(UUID.randomUUID().toString(), 3);
      List<Vm> vms = clusterBackend.findVms(clusterId, Optional.<Integer>absent()).getItems();
      assertEquals(vms.size(), vmIds.length);
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(vmIds),
          vms.stream().map(vm -> vm.getId()).collect(Collectors.toList())));
    }

    @Test
    public void testFindVmsWithPagination() throws Throwable {
      when(clusterManagerClient.getCluster(any(String.class))).thenReturn(buildCluster());

      String[] vmIds = createMockCluster(clusterId, 5);
      createMockCluster(UUID.randomUUID().toString(), 3);

      List<Vm> vms = new ArrayList<>();

      final int pageSize = 2;
      ResourceList<Vm> page = clusterBackend.findVms(clusterId, Optional.of(pageSize));
      vms.addAll(page.getItems());

      while (page.getNextPageLink() != null) {
        page = clusterBackend.getVmsPage(page.getNextPageLink());
        vms.addAll(page.getItems());
      }

      assertEquals(vms.size(), vmIds.length);
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(vmIds),
          vms.stream().map(vm -> vm.getId()).collect(Collectors.toList())));
    }

    @Test
    public void testFindVmsNoMatch() throws Throwable {
      when(clusterManagerClient.getCluster(any(String.class))).thenReturn(buildCluster());
      ResourceList<Vm> vms = clusterBackend.findVms(clusterId, Optional.<Integer>absent());
      assertEquals(vms.getItems().size(), 0);
    }

    @Test(expectedExceptions = ClusterNotFoundException.class)
    public void testClusterNotFound() throws Throwable {
      String clusterId = UUID.randomUUID().toString();
      when(clusterManagerClient.getCluster(clusterId)).thenThrow(new ClusterNotFoundException(clusterId));
      clusterBackend.findVms(clusterId, Optional.<Integer>absent());
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

      TaskEntity createdVmTaskEntity = vmDcpBackend.prepareVmCreate(projectId, spec);
      String vmId = createdVmTaskEntity.getEntityId();
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
