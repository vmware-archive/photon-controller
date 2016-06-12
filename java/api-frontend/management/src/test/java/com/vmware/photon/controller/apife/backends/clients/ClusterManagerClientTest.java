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

package com.vmware.photon.controller.apife.backends.clients;

import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.ClusterCreateSpec;
import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.SwarmClusterCreateTask;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests {@link ClusterManagerClient}.
 */
public class ClusterManagerClientTest {

  @Test
  private void dummy() {
  }

  /**
   * Tests for the createKubernetesCluster method.
   */
  public static class CreateKubernetesClusterTest extends PowerMockTestCase {
    @Mock
    private ClusterManagerXenonRestClient clusterManagerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ClusterManagerClient clusterManagerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      KubernetesClusterCreateTask task = new KubernetesClusterCreateTask();
      operation.setBody(task);

      when(clusterManagerXenonRestClient.post(any(String.class), any(KubernetesClusterCreateTask.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(ClusterService.State.class)))
          .thenReturn(null);

      List<ClusterConfigurationService.State> clusterConfigurations = new ArrayList<>();
      ClusterConfigurationService.State clusterConfiguration = new ClusterConfigurationService.State();
      clusterConfiguration.imageId = "imageId";
      clusterConfigurations.add(clusterConfiguration);
      when(apiFeXenonRestClient.queryDocuments(eq(ClusterConfigurationService.State.class), any(ImmutableMap.class)))
          .thenReturn(clusterConfigurations);

      clusterManagerClient = new ClusterManagerClient(clusterManagerXenonRestClient, apiFeXenonRestClient);
    }

    private static ClusterCreateSpec buildCreateSpec(boolean hasContainerNetwork) {
      ClusterCreateSpec createSpec = new ClusterCreateSpec();
      createSpec.setName("clusterName");
      createSpec.setType(ClusterType.KUBERNETES);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setVmNetworkId("vmNetworkId1");
      createSpec.setSlaveCount(50);
      Map<String, String> extendedProperty = new HashMap<>();
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1");
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2");
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128");
      extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP1, "10.1.0.3");
      extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP2, "10.1.0.4");
      extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP3, "10.1.0.5");
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "10.1.0.6");
      if (hasContainerNetwork) {
        extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16");
      }
      createSpec.setExtendedProperties(extendedProperty);
      return createSpec;
    }

    @Test
    public void testCreateKubernetesCluster() throws SpecInvalidException {
      ClusterCreateSpec spec = buildCreateSpec(true);
      KubernetesClusterCreateTask createTask = clusterManagerClient.createKubernetesCluster("projectId", spec);

      assertEquals(createTask.clusterId, notNull());
    }

    @Test
    public void testCreateKubernetesClusterMissingContainerNetwork() {
      ClusterCreateSpec spec = buildCreateSpec(false);
      try {
        clusterManagerClient.createKubernetesCluster("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @Test(dataProvider = "invalidContainerNetwork")
    public void testCreateKubernetesClusterInvalidContainerNetwork(String invalidContainerNetwork) {
      ClusterCreateSpec spec = buildCreateSpec(true);
      spec.getExtendedProperties().replace(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          invalidContainerNetwork);
      try {
        clusterManagerClient.createKubernetesCluster("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @DataProvider(name = "invalidContainerNetwork")
    public Object[][] getInvalidContainerNetwork() {
      return new Object[][]{
          {"foo"},
          {"foo/bar"},
          {"1.1.1.1/bar"},
          {"foo/16"},
          {"1.1.1.1/-1"},
          {"1.1.1.1/33"}
      };
    }
  }

  /**
   * Tests for the createMesosCluster method.
   */
  public static class CreateMesosClusterTest extends PowerMockTestCase {
    @Mock
    private ClusterManagerXenonRestClient clusterManagerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ClusterManagerClient clusterManagerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      KubernetesClusterCreateTask task = new KubernetesClusterCreateTask();
      operation.setBody(task);

      when(clusterManagerXenonRestClient.post(any(String.class), any(KubernetesClusterCreateTask.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(ClusterService.State.class)))
          .thenReturn(null);

      List<ClusterConfigurationService.State> clusterConfigurations = new ArrayList<>();
      ClusterConfigurationService.State clusterConfiguration = new ClusterConfigurationService.State();
      clusterConfiguration.imageId = "imageId";
      clusterConfigurations.add(clusterConfiguration);
      when(apiFeXenonRestClient.queryDocuments(eq(ClusterConfigurationService.State.class), any(ImmutableMap.class)))
          .thenReturn(clusterConfigurations);

      clusterManagerClient = new ClusterManagerClient(clusterManagerXenonRestClient, apiFeXenonRestClient);
    }

    private static ClusterCreateSpec buildCreateSpec(boolean hasZookeeper) {
      ClusterCreateSpec createSpec = new ClusterCreateSpec();
      createSpec.setName("clusterName");
      createSpec.setType(ClusterType.MESOS);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setVmNetworkId("vmNetworkId1");
      createSpec.setSlaveCount(50);
      Map<String, String> extendedProperty = new HashMap<>();
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1");
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2");
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128");
      if (hasZookeeper) {
        extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP1, "10.1.0.3");
        extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP2, "10.1.0.4");
        extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP3, "10.1.0.5");
      }
      createSpec.setExtendedProperties(extendedProperty);
      return createSpec;
    }

    @Test
    public void testCreateMesosCluster() throws SpecInvalidException {
      ClusterCreateSpec spec = buildCreateSpec(true);
      MesosClusterCreateTask createTask = clusterManagerClient.createMesosCluster("projectId", spec);

      assertEquals(createTask.clusterId, notNull());
    }

    @Test
    public void testCreateMesosClusterMissingExtendedProperty() {
      ClusterCreateSpec spec = buildCreateSpec(false);
      try {
        clusterManagerClient.createMesosCluster("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @Test(dataProvider = "invalidExtendedProperty")
    public void testCreateMesosClusterInvalidExtendedProperty(String propertyName, String propertyValue) {
      ClusterCreateSpec spec = buildCreateSpec(true);
      spec.getExtendedProperties().replace(propertyName, propertyValue);
      try {
        clusterManagerClient.createMesosCluster("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @DataProvider(name = "invalidExtendedProperty")
    public Object[][] getInvalidExtendedProperty() {
      return new Object[][]{
          {ClusterManagerConstants.EXTENDED_PROPERTY_DNS, "invalidDns"},
          {ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, "invalidGateway"},
          {ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, "invalidNetmask"},
          {ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP1, "invalidZookeeperIp1"},
          {ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP2, "invalidZookeeperIp2"},
          {ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP3, "invalidZookeeperIp3"}
      };
    }
  }

  /**
   * Tests for the createSwarmCluster method.
   */
  public static class CreateSwarmClusterTest extends PowerMockTestCase {
    @Mock
    private ClusterManagerXenonRestClient clusterManagerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ClusterManagerClient clusterManagerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      KubernetesClusterCreateTask task = new KubernetesClusterCreateTask();
      operation.setBody(task);

      when(clusterManagerXenonRestClient.post(any(String.class), any(KubernetesClusterCreateTask.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(ClusterService.State.class)))
          .thenReturn(null);

      List<ClusterConfigurationService.State> clusterConfigurations = new ArrayList<>();
      ClusterConfigurationService.State clusterConfiguration = new ClusterConfigurationService.State();
      clusterConfiguration.imageId = "imageId";
      clusterConfigurations.add(clusterConfiguration);
      when(apiFeXenonRestClient.queryDocuments(eq(ClusterConfigurationService.State.class), any(ImmutableMap.class)))
          .thenReturn(clusterConfigurations);

      clusterManagerClient = new ClusterManagerClient(clusterManagerXenonRestClient, apiFeXenonRestClient);
    }

    private static ClusterCreateSpec buildCreateSpec(boolean hasEtcd) {
      ClusterCreateSpec createSpec = new ClusterCreateSpec();
      createSpec.setName("clusterName");
      createSpec.setType(ClusterType.SWARM);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setVmNetworkId("vmNetworkId1");
      createSpec.setSlaveCount(50);
      Map<String, String> extendedProperty = new HashMap<>();
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1");
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2");
      extendedProperty.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128");
      if (hasEtcd) {
        extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP1, "10.1.0.3");
        extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP2, "10.1.0.4");
        extendedProperty.put(ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP3, "10.1.0.5");
      }
      createSpec.setExtendedProperties(extendedProperty);
      return createSpec;
    }

    @Test
    public void testCreateSwarmCluster() throws SpecInvalidException {
      ClusterCreateSpec spec = buildCreateSpec(true);
      SwarmClusterCreateTask createTask = clusterManagerClient.createSwarmCluster("projectId", spec);

      assertEquals(createTask.clusterId, notNull());
    }

    @Test
    public void testCreateSwarmClusterMissingExtendedProperty() {
      ClusterCreateSpec spec = buildCreateSpec(false);
      try {
        clusterManagerClient.createSwarmCluster("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @Test(dataProvider = "invalidExtendedProperty")
    public void testCreateSwarmClusterInvalidExtendedProperty(String propertyName, String propertyValue) {
      ClusterCreateSpec spec = buildCreateSpec(true);
      spec.getExtendedProperties().replace(propertyName, propertyValue);
      try {
        clusterManagerClient.createSwarmCluster("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @DataProvider(name = "invalidExtendedProperty")
    public Object[][] getInvalidExtendedProperty() {
      return new Object[][]{
          {ClusterManagerConstants.EXTENDED_PROPERTY_DNS, "invalidDns"},
          {ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, "invalidGateway"},
          {ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, "invalidNetmask"},
          {ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP1, "invalidEtcdIp1"},
          {ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP2, "invalidEtcdIp2"},
          {ClusterManagerClient.EXTENDED_PROPERTY_ETCD_IP3, "invalidEtcdIp3"}
      };
    }
  }

  /**
   * Tests for the get method.
   */
  public static class GetClusterTest extends PowerMockTestCase {
    private static final String clusterId = UUID.randomUUID().toString();
    @Mock
    private ClusterManagerXenonRestClient clusterManagerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ClusterManagerClient clusterManagerClient;
    private ClusterService.State clusterDocument;

    @BeforeMethod
    public void setUp() throws Throwable {
      clusterManagerClient = new ClusterManagerClient(clusterManagerXenonRestClient, apiFeXenonRestClient);
      clusterDocument = buildClusterDocument();
    }

    private static ClusterService.State buildClusterDocument() {
      ClusterService.State kc = new ClusterService.State();
      kc.documentSelfLink = "/cluster_manager/" + clusterId;
      kc.clusterName = "clusterName";
      kc.clusterType = ClusterType.KUBERNETES;
      kc.clusterState = ClusterState.READY;
      kc.projectId = "projectId";
      kc.slaveCount = 3;
      kc.extendedProperties = new HashMap();
      kc.extendedProperties.put(
          ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          "10.1.0.0/16");

      return kc;
    }

    private com.vmware.xenon.common.Operation buildOperation() {
      com.vmware.xenon.common.Operation operation = new com.vmware.xenon.common.Operation();
      operation.setBody(clusterDocument);

      return operation;
    }

    @Test
    public void testSuccess() throws DocumentNotFoundException, ExternalException {
      when(apiFeXenonRestClient.get(any(String.class))).thenReturn(buildOperation());

      Cluster cluster = clusterManagerClient.getCluster(clusterId);

      assertEquals(cluster.getId(), clusterId);
      assertEquals(cluster.getName(), clusterDocument.clusterName);
      assertEquals(cluster.getType(), ClusterType.KUBERNETES);
      assertEquals(cluster.getState(), ClusterState.READY);
      assertEquals(cluster.getProjectId(), clusterDocument.projectId);
      assertEquals(cluster.getSlaveCount(), clusterDocument.slaveCount.intValue());
      assertEquals(cluster.getExtendedProperties().get(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
          clusterDocument.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK));
    }

    @Test(expectedExceptions = ClusterNotFoundException.class)
    public void testException() throws DocumentNotFoundException, ClusterNotFoundException {
      when(apiFeXenonRestClient.get(any(String.class)))
          .thenThrow(mock(DocumentNotFoundException.class));

      clusterManagerClient.getCluster(clusterId);
    }

    @Test
    public void testGetClusters() throws Throwable {
      QueryTask queryTaskResult = new QueryTask();
      queryTaskResult.results = new ServiceDocumentQueryResult();
      queryTaskResult.results.documentLinks = Arrays.asList("Foo");

      NodeGroupBroadcastResponse queryResponse = new NodeGroupBroadcastResponse();
      queryResponse.jsonResponses = new HashMap<>();
      queryResponse.jsonResponses.put(
          new URI("FooLink"),
          Utils.toJson(queryTaskResult));

      Operation queryResult = new Operation();
      queryResult.setBody(queryResponse);

      when(apiFeXenonRestClient.postToBroadcastQueryService(any(QueryTask.QuerySpecification.class)))
          .thenReturn(queryResult);

      ClusterService.State clusterDocument = new ClusterService.State();
      clusterDocument.documentSelfLink = "/abc/de305d54-75b4-431b-adb2-eb6b9e546014";
      clusterDocument.clusterName = "clusterName";
      clusterDocument.projectId = "projectId";
      clusterDocument.slaveCount = 2;
      clusterDocument.clusterType = ClusterType.KUBERNETES;
      clusterDocument.clusterState = ClusterState.READY;
      clusterDocument.extendedProperties = new HashMap();
      clusterDocument.extendedProperties.put(
          ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          "10.10.10.10");

      ServiceDocumentQueryResult serviceDocumentQueryResult = new ServiceDocumentQueryResult();
      serviceDocumentQueryResult.documentCount = 1L;
      serviceDocumentQueryResult.documentLinks.add(clusterDocument.documentSelfLink);
      serviceDocumentQueryResult.documents = new HashMap<>();
      serviceDocumentQueryResult.documents.put(clusterDocument.documentSelfLink, clusterDocument);

      when(apiFeXenonRestClient.queryDocuments(anyObject(), anyObject(),
          eq(Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)), eq(true)))
          .thenReturn(serviceDocumentQueryResult);

      ResourceList<Cluster> clusters = clusterManagerClient.getClusters("projectId",
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertEquals(clusters.getItems().size(), 1);
      Cluster cluster = clusters.getItems().iterator().next();
      assertEquals(cluster.getId(), "de305d54-75b4-431b-adb2-eb6b9e546014");
      assertEquals(cluster.getName(), "clusterName");
      assertEquals(cluster.getType(), ClusterType.KUBERNETES);
      assertEquals(cluster.getState(), ClusterState.READY);
      assertEquals(cluster.getProjectId(), "projectId");
      assertEquals(cluster.getSlaveCount(), 2);
      assertEquals(cluster.getExtendedProperties().get(
              ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
          "10.10.10.10");
    }

    @Test
    public void testGetClustersPage() throws Throwable {
      ClusterService.State clusterDocument = new ClusterService.State();
      clusterDocument.documentSelfLink = "/abc/de305d54-75b4-431b-adb2-eb6b9e546014";
      clusterDocument.clusterName = "clusterName";
      clusterDocument.projectId = "projectId";
      clusterDocument.slaveCount = 2;
      clusterDocument.clusterType = ClusterType.KUBERNETES;
      clusterDocument.clusterState = ClusterState.READY;
      clusterDocument.extendedProperties = new HashMap();
      clusterDocument.extendedProperties.put(
          ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          "10.10.10.10");

      ServiceDocumentQueryResult serviceDocumentQueryResult = new ServiceDocumentQueryResult();
      serviceDocumentQueryResult.documentCount = 1L;
      serviceDocumentQueryResult.documentLinks.add(clusterDocument.documentSelfLink);
      serviceDocumentQueryResult.documents = new HashMap<>();
      serviceDocumentQueryResult.documents.put(clusterDocument.documentSelfLink, clusterDocument);

      String pageLink = UUID.randomUUID().toString();
      doReturn(serviceDocumentQueryResult).when(apiFeXenonRestClient).queryDocumentPage(pageLink);

      ResourceList<Cluster> clusters = clusterManagerClient.getClustersPages(pageLink);
      assertEquals(clusters.getItems().size(), 1);
      Cluster cluster = clusters.getItems().iterator().next();
      assertEquals(cluster.getId(), "de305d54-75b4-431b-adb2-eb6b9e546014");
      assertEquals(cluster.getName(), "clusterName");
      assertEquals(cluster.getType(), ClusterType.KUBERNETES);
      assertEquals(cluster.getState(), ClusterState.READY);
      assertEquals(cluster.getProjectId(), "projectId");
      assertEquals(cluster.getSlaveCount(), 2);
      assertEquals(cluster.getExtendedProperties().get(
              ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
          "10.10.10.10");
    }
  }
}
