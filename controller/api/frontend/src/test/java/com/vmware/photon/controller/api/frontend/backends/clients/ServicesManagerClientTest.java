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

package com.vmware.photon.controller.api.frontend.backends.clients;

import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ServiceNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceCreateSpec;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceConfigurationState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.servicesmanager.servicedocuments.HarborServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.KubernetesServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.MesosServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.servicedocuments.SwarmServiceCreateTaskState;
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
 * Tests {@link ServicesManagerClient}.
 */
public class ServicesManagerClientTest {

  @Test
  private void dummy() {
  }

  /**
   * Tests for the createKubernetesService method.
   */
  public static class CreateKubernetesServiceTest extends PowerMockTestCase {
    @Mock
    private PhotonControllerXenonRestClient photonControllerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ServicesManagerClient servicesManagerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      KubernetesServiceCreateTaskState task = new KubernetesServiceCreateTaskState();
      operation.setBody(task);

      when(photonControllerXenonRestClient.post(any(String.class), any(KubernetesServiceCreateTaskState.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(ServiceState.State.class)))
          .thenReturn(null);

      List<ServiceConfigurationState.State> serviceConfigurations = new ArrayList<>();
      ServiceConfigurationState.State serviceConfiguration = new ServiceConfigurationState.State();
      serviceConfiguration.imageId = "imageId";
      serviceConfigurations.add(serviceConfiguration);
      when(apiFeXenonRestClient.queryDocuments(eq(ServiceConfigurationState.State.class), any(ImmutableMap.class)))
          .thenReturn(serviceConfigurations);

      servicesManagerClient = new ServicesManagerClient(photonControllerXenonRestClient, apiFeXenonRestClient);
    }

    private static ServiceCreateSpec buildCreateSpec(boolean hasContainerNetwork) {
      ServiceCreateSpec createSpec = new ServiceCreateSpec();
      createSpec.setName("serviceName");
      createSpec.setType(ServiceType.KUBERNETES);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setVmNetworkId("vmNetworkId1");
      createSpec.setWorkerCount(50);
      Map<String, String> extendedProperty = new HashMap<>();
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128");
      extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP1, "10.1.0.3");
      extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP2, "10.1.0.4");
      extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP3, "10.1.0.5");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "10.1.0.6");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY, "test-key");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE, "example-ca-cert");
      if (hasContainerNetwork) {
        extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16");
      }
      createSpec.setExtendedProperties(extendedProperty);
      return createSpec;
    }

    @Test
    public void testCreateKubernetesService() throws SpecInvalidException {
      ServiceCreateSpec spec = buildCreateSpec(true);
      KubernetesServiceCreateTaskState createTask = servicesManagerClient.createKubernetesService("projectId", spec);

      assertEquals(createTask.serviceId, notNull());
    }

    @Test
    public void testCreateKubernetesServiceMissingContainerNetwork() {
      ServiceCreateSpec spec = buildCreateSpec(false);
      try {
        servicesManagerClient.createKubernetesService("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @Test(dataProvider = "invalidContainerNetwork")
    public void testCreateKubernetesServiceInvalidContainerNetwork(String invalidContainerNetwork) {
      ServiceCreateSpec spec = buildCreateSpec(true);
      spec.getExtendedProperties().replace(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          invalidContainerNetwork);
      try {
        servicesManagerClient.createKubernetesService("projectId", spec);
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
   * Tests for the createMesosService method.
   */
  public static class CreateMesosServiceTest extends PowerMockTestCase {
    @Mock
    private PhotonControllerXenonRestClient photonControllerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ServicesManagerClient servicesManagerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      KubernetesServiceCreateTaskState task = new KubernetesServiceCreateTaskState();
      operation.setBody(task);

      when(photonControllerXenonRestClient.post(any(String.class), any(KubernetesServiceCreateTaskState.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(ServiceState.State.class)))
          .thenReturn(null);

      List<ServiceConfigurationState.State> serviceConfigurations = new ArrayList<>();
      ServiceConfigurationState.State serviceConfiguration = new ServiceConfigurationState.State();
      serviceConfiguration.imageId = "imageId";
      serviceConfigurations.add(serviceConfiguration);
      when(apiFeXenonRestClient.queryDocuments(eq(ServiceConfigurationState.State.class), any(ImmutableMap.class)))
          .thenReturn(serviceConfigurations);

      servicesManagerClient = new ServicesManagerClient(photonControllerXenonRestClient, apiFeXenonRestClient);
    }

    private static ServiceCreateSpec buildCreateSpec(boolean hasZookeeper) {
      ServiceCreateSpec createSpec = new ServiceCreateSpec();
      createSpec.setName("serviceName");
      createSpec.setType(ServiceType.MESOS);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setVmNetworkId("vmNetworkId1");
      createSpec.setWorkerCount(50);
      Map<String, String> extendedProperty = new HashMap<>();
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128");
      if (hasZookeeper) {
        extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP1, "10.1.0.3");
        extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP2, "10.1.0.4");
        extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP3, "10.1.0.5");
      }
      createSpec.setExtendedProperties(extendedProperty);
      return createSpec;
    }

    @Test
    public void testCreateMesosService() throws SpecInvalidException {
      ServiceCreateSpec spec = buildCreateSpec(true);
      MesosServiceCreateTaskState createTask = servicesManagerClient.createMesosService("projectId", spec);

      assertEquals(createTask.serviceId, notNull());
    }

    @Test
    public void testCreateMesosServiceMissingExtendedProperty() {
      ServiceCreateSpec spec = buildCreateSpec(false);
      try {
        servicesManagerClient.createMesosService("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @Test(dataProvider = "invalidExtendedProperty")
    public void testCreateMesosServiceInvalidExtendedProperty(String propertyName, String propertyValue) {
      ServiceCreateSpec spec = buildCreateSpec(true);
      spec.getExtendedProperties().replace(propertyName, propertyValue);
      try {
        servicesManagerClient.createMesosService("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @DataProvider(name = "invalidExtendedProperty")
    public Object[][] getInvalidExtendedProperty() {
      return new Object[][]{
          {ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "invalidDns"},
          {ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "invalidGateway"},
          {ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "invalidNetmask"},
          {ServicesManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP1, "invalidZookeeperIp1"},
          {ServicesManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP2, "invalidZookeeperIp2"},
          {ServicesManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP3, "invalidZookeeperIp3"}
      };
    }
  }

  /**
   * Tests for the createSwarmService method.
   */
  public static class CreateSwarmServiceTest extends PowerMockTestCase {
    @Mock
    private PhotonControllerXenonRestClient photonControllerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ServicesManagerClient servicesManagerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      KubernetesServiceCreateTaskState task = new KubernetesServiceCreateTaskState();
      operation.setBody(task);

      when(photonControllerXenonRestClient.post(any(String.class), any(KubernetesServiceCreateTaskState.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(ServiceState.State.class)))
          .thenReturn(null);

      List<ServiceConfigurationState.State> serviceConfigurations = new ArrayList<>();
      ServiceConfigurationState.State serviceConfiguration = new ServiceConfigurationState.State();
      serviceConfiguration.imageId = "imageId";
      serviceConfigurations.add(serviceConfiguration);
      when(apiFeXenonRestClient.queryDocuments(eq(ServiceConfigurationState.State.class), any(ImmutableMap.class)))
          .thenReturn(serviceConfigurations);

      servicesManagerClient = new ServicesManagerClient(photonControllerXenonRestClient, apiFeXenonRestClient);
    }

    private static ServiceCreateSpec buildCreateSpec(boolean hasEtcd) {
      ServiceCreateSpec createSpec = new ServiceCreateSpec();
      createSpec.setName("serviceName");
      createSpec.setType(ServiceType.SWARM);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setVmNetworkId("vmNetworkId1");
      createSpec.setWorkerCount(50);
      Map<String, String> extendedProperty = new HashMap<>();
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128");
      if (hasEtcd) {
        extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP1, "10.1.0.3");
        extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP2, "10.1.0.4");
        extendedProperty.put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP3, "10.1.0.5");
      }
      createSpec.setExtendedProperties(extendedProperty);
      return createSpec;
    }

    @Test
    public void testCreateSwarmService() throws SpecInvalidException {
      ServiceCreateSpec spec = buildCreateSpec(true);
      SwarmServiceCreateTaskState createTask = servicesManagerClient.createSwarmService("projectId", spec);

      assertEquals(createTask.serviceId, notNull());
    }

    @Test
    public void testCreateSwarmServiceMissingExtendedProperty() {
      ServiceCreateSpec spec = buildCreateSpec(false);
      try {
        servicesManagerClient.createSwarmService("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @Test(dataProvider = "invalidExtendedProperty")
    public void testCreateSwarmServiceInvalidExtendedProperty(String propertyName, String propertyValue) {
      ServiceCreateSpec spec = buildCreateSpec(true);
      spec.getExtendedProperties().replace(propertyName, propertyValue);
      try {
        servicesManagerClient.createSwarmService("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @DataProvider(name = "invalidExtendedProperty")
    public Object[][] getInvalidExtendedProperty() {
      return new Object[][]{
          {ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "invalidDns"},
          {ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "invalidGateway"},
          {ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "invalidNetmask"},
          {ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP1, "invalidEtcdIp1"},
          {ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP2, "invalidEtcdIp2"},
          {ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP3, "invalidEtcdIp3"}
      };
    }
  }

  /**
   * Tests for the createHarborService method.
   */
  public static class CreateHarborServiceTest extends PowerMockTestCase {
    @Mock
    private PhotonControllerXenonRestClient photonControllerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ServicesManagerClient servicesManagerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      HarborServiceCreateTaskState task = new HarborServiceCreateTaskState();
      operation.setBody(task);

      when(photonControllerXenonRestClient.post(any(String.class), any(HarborServiceCreateTaskState.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(ServiceState.State.class)))
          .thenReturn(null);

      List<ServiceConfigurationState.State> serviceConfigurations = new ArrayList<>();
      ServiceConfigurationState.State serviceConfiguration = new ServiceConfigurationState.State();
      serviceConfiguration.imageId = "imageId";
      serviceConfigurations.add(serviceConfiguration);
      when(apiFeXenonRestClient.queryDocuments(eq(ServiceConfigurationState.State.class), any(ImmutableMap.class)))
          .thenReturn(serviceConfigurations);

      servicesManagerClient = new ServicesManagerClient(photonControllerXenonRestClient, apiFeXenonRestClient);
    }

    private static ServiceCreateSpec buildCreateSpec(boolean setMasterIp) {
      ServiceCreateSpec createSpec = new ServiceCreateSpec();
      createSpec.setName("serviceName");
      createSpec.setType(ServiceType.HARBOR);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setVmNetworkId("vmNetworkId1");
      createSpec.setWorkerCount(0);
      Map<String, String> extendedProperty = new HashMap<>();
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY, "test-key");
      extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD, "admin-password");
      if (setMasterIp) {
        extendedProperty.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "10.1.2.3");
      }
      createSpec.setExtendedProperties(extendedProperty);
      return createSpec;
    }

    @Test
    public void testCreateHarborService() throws SpecInvalidException {
      ServiceCreateSpec spec = buildCreateSpec(true);
      HarborServiceCreateTaskState createTask = servicesManagerClient.createHarborService("projectId", spec);

      assertEquals(createTask.serviceId, notNull());
    }

    @Test
    public void testCreateHarborServiceMissingExtendedProperty() {
      ServiceCreateSpec spec = buildCreateSpec(false);
      try {
        servicesManagerClient.createHarborService("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @Test(dataProvider = "invalidExtendedProperty")
    public void testCreateHarborServiceInvalidExtendedProperty(String propertyName, String propertyValue) {
      ServiceCreateSpec spec = buildCreateSpec(true);
      spec.getExtendedProperties().replace(propertyName, propertyValue);
      try {
        servicesManagerClient.createHarborService("projectId", spec);
        Assert.fail("expect exception");
      } catch (SpecInvalidException ex) {
      }
    }

    @DataProvider(name = "invalidExtendedProperty")
    public Object[][] getInvalidExtendedProperty() {
      return new Object[][]{
          {ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "invalidDns"},
          {ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "invalidGateway"},
          {ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "invalidNetmask"},
      };
    }
  }

  /**
   * Tests for the get method.
   */
  public static class GetServiceTest extends PowerMockTestCase {
    private static final String serviceId = UUID.randomUUID().toString();
    @Mock
    private PhotonControllerXenonRestClient photonControllerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private ServicesManagerClient servicesManagerClient;
    private ServiceState.State serviceDocument;

    @BeforeMethod
    public void setUp() throws Throwable {
      servicesManagerClient = new ServicesManagerClient(photonControllerXenonRestClient, apiFeXenonRestClient);
      serviceDocument = buildServiceDocument();
    }

    private static ServiceState.State buildServiceDocument() {
      ServiceState.State kc = new ServiceState.State();
      kc.documentSelfLink = "/services_manager/" + serviceId;
      kc.serviceName = "serviceName";
      kc.serviceType = ServiceType.KUBERNETES;
      kc.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
      kc.projectId = "projectId";
      kc.workerCount = 3;
      kc.extendedProperties = new HashMap<>();
      kc.extendedProperties.put(
          ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          "10.1.0.0/16");

      return kc;
    }

    private com.vmware.xenon.common.Operation buildOperation() {
      com.vmware.xenon.common.Operation operation = new com.vmware.xenon.common.Operation();
      operation.setBody(serviceDocument);

      return operation;
    }

    @Test
    public void testSuccess() throws DocumentNotFoundException, ExternalException {
      when(apiFeXenonRestClient.get(any(String.class))).thenReturn(buildOperation());

      Service service = servicesManagerClient.getService(serviceId);

      assertEquals(service.getId(), serviceId);
      assertEquals(service.getName(), serviceDocument.serviceName);
      assertEquals(service.getType(), ServiceType.KUBERNETES);
      assertEquals(service.getState(), com.vmware.photon.controller.api.model.ServiceState.READY);
      assertEquals(service.getProjectId(), serviceDocument.projectId);
      assertEquals(service.getWorkerCount(), serviceDocument.workerCount.intValue());
      assertEquals(service.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
          serviceDocument.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK));
    }

    @Test(expectedExceptions = ServiceNotFoundException.class)
    public void testException() throws DocumentNotFoundException, ServiceNotFoundException {
      when(apiFeXenonRestClient.get(any(String.class)))
          .thenThrow(mock(DocumentNotFoundException.class));

      servicesManagerClient.getService(serviceId);
    }

    @Test
    public void testGetServices() throws Throwable {
      QueryTask queryTaskResult = new QueryTask();
      queryTaskResult.results = new ServiceDocumentQueryResult();
      queryTaskResult.results.documentLinks = Arrays.asList("Foo");

      NodeGroupBroadcastResponse queryResponse = new NodeGroupBroadcastResponse();
      queryResponse.jsonResponses = new HashMap<>();
      queryResponse.jsonResponses.put(
          new URI("FooLink"),
          Utils.toJson(false, false, queryTaskResult));

      Operation queryResult = new Operation();
      queryResult.setBody(queryResponse);

      when(apiFeXenonRestClient.postToBroadcastQueryService(any(QueryTask.QuerySpecification.class)))
          .thenReturn(queryResult);

      ServiceState.State serviceDocument = new ServiceState.State();
      serviceDocument.documentSelfLink = "/abc/de305d54-75b4-431b-adb2-eb6b9e546014";
      serviceDocument.serviceName = "serviceName";
      serviceDocument.projectId = "projectId";
      serviceDocument.workerCount = 2;
      serviceDocument.serviceType = ServiceType.KUBERNETES;
      serviceDocument.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
      serviceDocument.extendedProperties = new HashMap<>();
      serviceDocument.extendedProperties.put(
          ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          "10.10.10.10");

      ServiceDocumentQueryResult serviceDocumentQueryResult = new ServiceDocumentQueryResult();
      serviceDocumentQueryResult.documentCount = 1L;
      serviceDocumentQueryResult.documentLinks.add(serviceDocument.documentSelfLink);
      serviceDocumentQueryResult.documents = new HashMap<>();
      serviceDocumentQueryResult.documents.put(serviceDocument.documentSelfLink, serviceDocument);

      when(apiFeXenonRestClient.queryDocuments(anyObject(), anyObject(),
          eq(Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)), eq(true)))
          .thenReturn(serviceDocumentQueryResult);

      ResourceList<Service> services = servicesManagerClient.getServices("projectId",
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertEquals(services.getItems().size(), 1);
      Service service = services.getItems().iterator().next();
      assertEquals(service.getId(), "de305d54-75b4-431b-adb2-eb6b9e546014");
      assertEquals(service.getName(), "serviceName");
      assertEquals(service.getType(), ServiceType.KUBERNETES);
      assertEquals(service.getState(), com.vmware.photon.controller.api.model.ServiceState.READY);
      assertEquals(service.getProjectId(), "projectId");
      assertEquals(service.getWorkerCount(), 2);
      assertEquals(service.getExtendedProperties().get(
              ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
          "10.10.10.10");
    }

    @Test
    public void testGetServicesPage() throws Throwable {
      ServiceState.State serviceDocument = new ServiceState.State();
      serviceDocument.documentSelfLink = "/abc/de305d54-75b4-431b-adb2-eb6b9e546014";
      serviceDocument.serviceName = "serviceName";
      serviceDocument.projectId = "projectId";
      serviceDocument.workerCount = 2;
      serviceDocument.serviceType = ServiceType.KUBERNETES;
      serviceDocument.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
      serviceDocument.extendedProperties = new HashMap<>();
      serviceDocument.extendedProperties.put(
          ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
          "10.10.10.10");

      ServiceDocumentQueryResult serviceDocumentQueryResult = new ServiceDocumentQueryResult();
      serviceDocumentQueryResult.documentCount = 1L;
      serviceDocumentQueryResult.documentLinks.add(serviceDocument.documentSelfLink);
      serviceDocumentQueryResult.documents = new HashMap<>();
      serviceDocumentQueryResult.documents.put(serviceDocument.documentSelfLink, serviceDocument);

      String pageLink = UUID.randomUUID().toString();
      doReturn(serviceDocumentQueryResult).when(apiFeXenonRestClient).queryDocumentPage(pageLink);

      ResourceList<Service> services = servicesManagerClient.getServicesPages(pageLink);
      assertEquals(services.getItems().size(), 1);
      Service service = services.getItems().iterator().next();
      assertEquals(service.getId(), "de305d54-75b4-431b-adb2-eb6b9e546014");
      assertEquals(service.getName(), "serviceName");
      assertEquals(service.getType(), ServiceType.KUBERNETES);
      assertEquals(service.getState(), com.vmware.photon.controller.api.model.ServiceState.READY);
      assertEquals(service.getProjectId(), "projectId");
      assertEquals(service.getWorkerCount(), 2);
      assertEquals(service.getExtendedProperties().get(
              ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
          "10.10.10.10");
    }
  }
}
