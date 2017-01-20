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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.TestModule;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.clients.ServicesManagerClient;
import com.vmware.photon.controller.api.frontend.commands.CommandTestModule;
import com.vmware.photon.controller.api.frontend.commands.steps.HarborServiceCreateStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.KubernetesServiceCreateStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.ServiceDeleteStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.ServiceResizeStepCmd;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ServiceNotFoundException;
import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.model.ImageState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceCreateSpec;
import com.vmware.photon.controller.api.model.ServiceResizeOperation;
import com.vmware.photon.controller.api.model.ServiceState;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.api.model.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.resource.gen.ImageReplication;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.util.ServicesUtil;

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
 * Tests {@link ServiceBackend}.
 */
public class ServiceBackendTest {
  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
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
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }


  @Test
  private void dummy() {
  }

  private static Service buildService(String serviceId) {
    Service service = new Service();
    service.setId(serviceId);
    service.setName("serviceName");
    service.setType(ServiceType.KUBERNETES);
    service.setState(ServiceState.READY);
    service.setProjectId("projectId");
    service.setWorkerCount(2);
    service.setExtendedProperties(ImmutableMap.of(
        ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "12.12.12.12"));

    return service;
  }

  /**
   * Tests for the create method.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateServiceTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    private ServicesManagerClient servicesManagerClient;
    private ServiceCreateSpec createSpec;
    private ServiceBackend serviceBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      servicesManagerClient = mock(ServicesManagerClient.class);
      serviceBackend = new ServiceBackend(servicesManagerClient, taskBackend, vmBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    private static ServiceCreateSpec buildCreateSpec(ServiceType serviceType) {
      ServiceCreateSpec createSpec = new ServiceCreateSpec();
      createSpec.setName("serviceName");
      createSpec.setType(serviceType);
      createSpec.setVmFlavor("vmFlavor1");
      createSpec.setDiskFlavor("diskFlavor1");
      createSpec.setWorkerCount(50);
      createSpec.setExtendedProperties(ImmutableMap.of(
          ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16"));
      return createSpec;
    }

    @Test
    public void testKubernetesService() throws Throwable {
      createSpec = buildCreateSpec(ServiceType.KUBERNETES);
      TaskEntity taskEntity = serviceBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 5);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_KUBERNETES_SERVICE_INITIATE);
      ServiceCreateSpec createSpecActual = (ServiceCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 5);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_KUBERNETES_SERVICE_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_KUBERNETES_SERVICE_SETUP_ETCD);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_KUBERNETES_SERVICE_SETUP_MASTER);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation
          .CREATE_KUBERNETES_SERVICE_UPDATE_EXTENDED_PROPERTIES);
      assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.CREATE_KUBERNETES_SERVICE_SETUP_WORKERS);
    }

    @Test
    public void testMesosService() throws Throwable {
      createSpec = buildCreateSpec(ServiceType.MESOS);
      TaskEntity taskEntity = serviceBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 5);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_MESOS_SERVICE_INITIATE);
      ServiceCreateSpec createSpecActual = (ServiceCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 5);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_MESOS_SERVICE_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_MESOS_SERVICE_SETUP_ZOOKEEPERS);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_MESOS_SERVICE_SETUP_MASTERS);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_MESOS_SERVICE_SETUP_MARATHON);
      assertEquals(taskEntity.getSteps().get(4).getOperation(), Operation.CREATE_MESOS_SERVICE_SETUP_WORKERS);
    }

    @Test
    public void testSwarmService() throws Throwable {
      createSpec = buildCreateSpec(ServiceType.SWARM);
      TaskEntity taskEntity = serviceBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 4);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_SWARM_SERVICE_INITIATE);
      ServiceCreateSpec createSpecActual = (ServiceCreateSpec) initiateStepEntity
          .getTransientResource(KubernetesServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 4);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_SWARM_SERVICE_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_SWARM_SERVICE_SETUP_ETCD);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.CREATE_SWARM_SERVICE_SETUP_MASTER);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.CREATE_SWARM_SERVICE_SETUP_WORKERS);
    }

    @Test
    public void testHarborService() throws Throwable {
      createSpec = buildCreateSpec(ServiceType.HARBOR);
      TaskEntity taskEntity = serviceBackend.create("projectId", createSpec);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 3);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.CREATE_HARBOR_SERVICE_INITIATE);
      ServiceCreateSpec createSpecActual = (ServiceCreateSpec) initiateStepEntity
          .getTransientResource(HarborServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY);
      Assert.assertNotNull(createSpecActual);
      assertEquals(createSpecActual.getName(), createSpec.getName());
      assertEquals(createSpecActual.getType(), createSpec.getType());

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 3);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.CREATE_HARBOR_SERVICE_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.CREATE_HARBOR_SERVICE_SETUP_HARBOR);
      assertEquals(taskEntity.getSteps().get(2).getOperation(),
          Operation.CREATE_HARBOR_SERVICE_UPDATE_EXTENDED_PROPERTIES);
    }
  }

  /**
   * Tests for the delete method.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteServiceTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmXenonBackend vmXenonBackend;

    private ServicesManagerClient servicesManagerClient;
    private ServiceBackend serviceBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      servicesManagerClient = mock(ServicesManagerClient.class);
      serviceBackend = new ServiceBackend(servicesManagerClient, taskBackend, vmXenonBackend);
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
      String serviceId = UUID.randomUUID().toString();

      Service service = buildService(serviceId);
      when(servicesManagerClient.getService(serviceId)).thenReturn(service);

      TaskEntity taskEntity = serviceBackend.delete(serviceId);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      assertEquals(taskEntity.getSteps().size(), 4);
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);
      assertEquals(initiateStepEntity.getOperation(), Operation.DELETE_SERVICE_INITIATE);
      String serviceIdToDelete = (String) initiateStepEntity
          .getTransientResource(ServiceDeleteStepCmd.SERVICE_ID_RESOURCE_KEY);
      assertEquals(serviceIdToDelete, serviceId);

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 4);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.DELETE_SERVICE_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.DELETE_SERVICE_UPDATE_SERVICE_DOCUMENT);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.DELETE_SERVICE_DELETE_VMS);
      assertEquals(taskEntity.getSteps().get(3).getOperation(), Operation.DELETE_SERVICE_DOCUMENT);
    }

    @Test(expectedExceptions = ServiceNotFoundException.class)
    public void testServiceNotFound() throws Throwable {
      String serviceId = UUID.randomUUID().toString();
      when(servicesManagerClient.getService(serviceId)).thenThrow(new ServiceNotFoundException(serviceId));
      serviceBackend.delete(serviceId);
    }
  }

  /**
   * Tests for the resize method.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ResizeServiceTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmXenonBackend vmXenonBackend;

    private ServicesManagerClient servicesManagerClient;
    private ServiceBackend serviceBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      servicesManagerClient = mock(ServicesManagerClient.class);
      serviceBackend = new ServiceBackend(servicesManagerClient, taskBackend, vmXenonBackend);
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
      String serviceId = UUID.randomUUID().toString();

      Service service = buildService(serviceId);
      when(servicesManagerClient.getService(serviceId)).thenReturn(service);

      ServiceResizeOperation resizeOperation = new ServiceResizeOperation();
      resizeOperation.setNewWorkerCount(10);

      TaskEntity taskEntity = serviceBackend.resize(serviceId, resizeOperation);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());

      // verify transient resources are set correctly
      StepEntity initiateStepEntity = taskEntity.getSteps().get(0);

      String serviceIdToResize = (String) initiateStepEntity
          .getTransientResource(ServiceResizeStepCmd.SERVICE_ID_RESOURCE_KEY);
      assertEquals(serviceIdToResize, serviceId);

      ServiceResizeOperation resizeOperationReturned = (ServiceResizeOperation) initiateStepEntity
          .getTransientResource(ServiceResizeStepCmd.RESIZE_OPERATION_RESOURCE_KEY);
      assertEquals(resizeOperationReturned, resizeOperation);

      assertEquals(taskEntity.getState(), TaskEntity.State.QUEUED);

      // verify that task steps are created successfully
      assertEquals(taskEntity.getSteps().size(), 3);
      assertEquals(taskEntity.getSteps().get(0).getOperation(), Operation.RESIZE_SERVICE_INITIATE);
      assertEquals(taskEntity.getSteps().get(1).getOperation(), Operation.RESIZE_SERVICE_INITIALIZE_SERVICE);
      assertEquals(taskEntity.getSteps().get(2).getOperation(), Operation.RESIZE_SERVICE_RESIZE);
    }

    @Test(expectedExceptions = ServiceNotFoundException.class)
    public void testServiceNotFound() throws Throwable {
      String serviceId = UUID.randomUUID().toString();
      when(servicesManagerClient.getService(serviceId)).thenThrow(new ServiceNotFoundException(serviceId));

      ServiceResizeOperation resizeOperation = new ServiceResizeOperation();
      resizeOperation.setNewWorkerCount(10);

      serviceBackend.resize(serviceId, resizeOperation);
    }
  }

  /**
   * Tests for trigger maintenance.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class, CommandTestModule.class})
  public static class TriggerMaintenanceTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmXenonBackend vmXenonBackend;

    private ServicesManagerClient servicesManagerClient;
    private ServiceBackend serviceBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      servicesManagerClient = mock(ServicesManagerClient.class);
      serviceBackend = new ServiceBackend(servicesManagerClient, taskBackend, vmXenonBackend);
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
      String serviceId = UUID.randomUUID().toString();

      Service service = buildService(serviceId);
      when(servicesManagerClient.getService(serviceId)).thenReturn(service);

      TaskEntity taskEntity = serviceBackend.triggerMaintenance(serviceId);
      Assert.assertNotNull(taskEntity);
      Assert.assertNotNull(taskEntity.getId());
      assertEquals(taskEntity.getOperation(), Operation.TRIGGER_SERVICE_MAINTENANCE);
    }

    @Test(expectedExceptions = ServiceNotFoundException.class)
    public void testServiceNotFound() throws Throwable {
      String serviceId = UUID.randomUUID().toString();
      when(servicesManagerClient.getService(serviceId)).thenThrow(new ServiceNotFoundException(serviceId));

      serviceBackend.triggerMaintenance(serviceId);
    }
  }

  /**
   * Tests for the find method.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class, CommandTestModule.class})
  public static class FindTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    private ServicesManagerClient servicesManagerClient;
    private ServiceBackend serviceBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      servicesManagerClient = mock(ServicesManagerClient.class);
      serviceBackend = new ServiceBackend(servicesManagerClient, taskBackend, vmBackend);
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
      Service c1 = buildService("serviceId1");
      Service c2 = buildService("serviceId2");

      String projectId = "projectId";
      String nextPageLink = UUID.randomUUID().toString();
      when(servicesManagerClient.getServices(projectId, Optional.of(1)))
          .thenReturn(new ResourceList<Service>(Arrays.asList(c1), nextPageLink, null));
      when(servicesManagerClient.getServicesPages(nextPageLink))
          .thenReturn(new ResourceList<Service>(Arrays.asList(c2)));

      ResourceList<Service> services = serviceBackend.find("projectId", Optional.of(1));
      assertEquals(services.getItems().size(), 1);
      assertEquals(services.getItems().get(0), c1);
      assertEquals(services.getNextPageLink(), nextPageLink);

      services = serviceBackend.getServicesPage(nextPageLink);
      assertEquals(services.getItems().size(), 1);
      assertEquals(services.getItems().get(0), c2);
    }

    @Test
    public void testNoServicesFound() throws Throwable {
      String projectId = "projectId";
      when(servicesManagerClient.getServices(projectId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
          .thenReturn(new ResourceList<>(new ArrayList<>()));

      ResourceList<Service> services = serviceBackend.find("projectId",
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertEquals(services.getItems().size(), 0);
    }

    @Test
    public void testGetNumberServicesByDeployment() {
      int serviceNum = 3;
      when(servicesManagerClient.getNumber(Optional.<String>absent())).thenReturn(serviceNum);
      assertEquals(serviceBackend.getNumberServices(), serviceNum);
    }

    @Test
    public void testGetNumberServicesByValidProjectId() {
      int serviceNum = 1;
      String projectId = "projectId";
      when(servicesManagerClient.getNumber(Optional.of(projectId))).thenReturn(serviceNum);
      assertEquals(serviceBackend.getNumberServicesByProject(projectId), serviceNum);
    }

    @Test
    public void testGetNumberServicesByInvalidProjectId() {
      int serviceNum = 0;
      String projectId = "not_exist_project_id";
      when(servicesManagerClient.getNumber(Optional.of(projectId))).thenReturn(serviceNum);
      assertEquals(serviceBackend.getNumberServicesByProject(projectId), serviceNum);
    }
  }

  /**
   * Tests for the findVms method.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class, CommandTestModule.class})
  public static class FindVmTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    private ServiceBackend serviceBackend;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    private ServicesManagerClient servicesManagerClient;

    private String projectId;
    private String serviceId = UUID.randomUUID().toString();
    private String imageId;

    @BeforeMethod()
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "vmware");

      QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
      XenonBackendTestHelper.createTenantResourceTicket(resourceTicketXenonBackend,
          tenantId, "rt1", ImmutableList.of(ticketLimit));

      QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
      projectId = XenonBackendTestHelper.createProject(projectXenonBackend,
          "staging", tenantId, "rt1", ImmutableList.of(projectLimit));

      XenonBackendTestHelper.createFlavors(flavorXenonBackend, flavorLoader.getAllFlavors());

      ImageService.State imageServiceState = new ImageService.State();
      imageServiceState.name = "image-1";
      imageServiceState.state = ImageState.READY;
      imageServiceState.size = 1024L * 1024L;
      imageServiceState.replicationType = ImageReplication.EAGER;
      imageServiceState.imageSettings = new ArrayList<>();
      ImageService.State.ImageSetting imageSetting = new ImageService.State.ImageSetting();
      imageSetting.name = "n1";
      imageSetting.defaultValue = "v1";
      imageServiceState.imageSettings.add(imageSetting);
      imageSetting = new ImageService.State.ImageSetting();
      imageSetting.name = "n2";
      imageSetting.defaultValue = "v2";
      imageServiceState.imageSettings.add(imageSetting);

      com.vmware.xenon.common.Operation result = xenonClient.post(ImageServiceFactory.SELF_LINK, imageServiceState);

      ImageService.State createdImageState = result.getBody(ImageService.State.class);

      imageId = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

      servicesManagerClient = mock(ServicesManagerClient.class);
      serviceBackend = new ServiceBackend(servicesManagerClient, taskBackend, vmXenonBackend);
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
      when(servicesManagerClient.getService(any(String.class))).thenReturn(buildService());

      String[] vmIds = createMockService(serviceId, 5);
      createMockService(UUID.randomUUID().toString(), 3);
      List<Vm> vms = serviceBackend.findVms(serviceId, Optional.<Integer>absent()).getItems();
      assertEquals(vms.size(), vmIds.length);
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(vmIds),
          vms.stream().map(vm -> vm.getId()).collect(Collectors.toList())));
    }

    @Test
    public void testFindVmsWithPagination() throws Throwable {
      when(servicesManagerClient.getService(any(String.class))).thenReturn(buildService());

      String[] vmIds = createMockService(serviceId, 5);
      createMockService(UUID.randomUUID().toString(), 3);

      List<Vm> vms = new ArrayList<>();

      final int pageSize = 2;
      ResourceList<Vm> page = serviceBackend.findVms(serviceId, Optional.of(pageSize));
      vms.addAll(page.getItems());

      while (page.getNextPageLink() != null) {
        page = serviceBackend.getVmsPage(page.getNextPageLink());
        vms.addAll(page.getItems());
      }

      assertEquals(vms.size(), vmIds.length);
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(vmIds),
          vms.stream().map(vm -> vm.getId()).collect(Collectors.toList())));
    }

    @Test
    public void testFindVmsNoMatch() throws Throwable {
      when(servicesManagerClient.getService(any(String.class))).thenReturn(buildService());
      ResourceList<Vm> vms = serviceBackend.findVms(serviceId, Optional.<Integer>absent());
      assertEquals(vms.getItems().size(), 0);
    }

    @Test(expectedExceptions = ServiceNotFoundException.class)
    public void testServiceNotFound() throws Throwable {
      String serviceId = UUID.randomUUID().toString();
      when(servicesManagerClient.getService(serviceId)).thenThrow(new ServiceNotFoundException(serviceId));
      serviceBackend.findVms(serviceId, Optional.<Integer>absent());
    }

    private String createVm(String serviceId) throws Exception {
      AttachedDiskCreateSpec disk1 =
          new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();

      VmCreateSpec spec = new VmCreateSpec();
      spec.setName("test-vm");
      spec.setFlavor("core-100");
      spec.setSourceImageId(imageId);
      spec.setAttachedDisks(ImmutableList.of(disk1));
      spec.setTags(ImmutableSet.of(ServicesUtil.createServiceTag(serviceId)));

      TaskEntity createdVmTaskEntity = vmXenonBackend.prepareVmCreate(projectId, spec);
      String vmId = createdVmTaskEntity.getEntityId();
      return vmId;
    }

    private String[] createMockService(String id, int serviceSize) throws Exception {
      List<String> vmIds = new ArrayList<>();
      for (int i = 0; i < serviceSize; i++) {
        String vmId = createVm(id);
        vmIds.add(vmId);
      }
      return vmIds.toArray(new String[vmIds.size()]);
    }

    private Service buildService() {
      Service service = new Service();
      service.setProjectId(projectId);
      service.setName("serviceName");
      return service;
    }
  }
}
