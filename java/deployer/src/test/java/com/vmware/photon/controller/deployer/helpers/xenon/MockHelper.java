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

package com.vmware.photon.controller.deployer.helpers.xenon;

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.AgentStatusCode;
import com.vmware.photon.controller.agent.gen.AgentStatusResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.agent.gen.UpgradeResultCode;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmMetadata;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.client.resource.ImagesApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.TenantsApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TenantService;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.auth.AuthException;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthChecker;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.mock.AgentControlClientMock;
import com.vmware.photon.controller.deployer.xenon.mock.HostClientMock;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.host.gen.SetHostModeResultCode;
import com.vmware.photon.controller.nsxclient.models.FabricNode;
import com.vmware.photon.controller.nsxclient.models.FabricNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportNode;
import com.vmware.photon.controller.nsxclient.models.TransportNodeState;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Network;
import com.vmware.xenon.common.Service;

import com.github.dockerjava.api.DockerException;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * This class provides utility functions for Mocks.
 */
public class MockHelper {

  public static void mockHostClient(AgentControlClientFactory agentControlClientFactory,
                                    HostClientFactory hostClientFactory,
                                    boolean isSuccess)
      throws Throwable {

    AgentControlClient agentControlClient;
    HostClient hostClient;
    if (isSuccess) {
      HostConfig hostConfig = new HostConfig();
      hostConfig.setCpu_count(2);
      hostConfig.setMemory_mb(4096);
      hostConfig.setEsx_version("ESX Version");

      agentControlClient = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .upgradeResultCode(UpgradeResultCode.OK)
          .build();

      hostClient = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.OK)
          .setHostModeResultCode(SetHostModeResultCode.OK)
          .hostConfig(hostConfig)
          .build();
    } else {
      agentControlClient = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.SYSTEM_ERROR)
          .provisionFailure(new Exception("ProvisionHost throws exception"))
          .upgradeResultCode(UpgradeResultCode.SYSTEM_ERROR)
          .upgradeFailure(new Exception("UpgradeHost throws exception"))
          .agentStatusCode(AgentStatusCode.UPGRADING)
          .getAgentStatusFailure(new Exception("GetAgentStatus throws Exception"))
          .build();

      hostClient = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.SYSTEM_ERROR)
          .getConfigFailure(new Exception("GetHost throws exception"))
          .setHostModeResultCode(SetHostModeResultCode.SYSTEM_ERROR)
          .setHostModeFailure(new Exception("SetHostMode throws exception"))
          .build();
    }

    doReturn(agentControlClient).when(agentControlClientFactory).create();
    doReturn(hostClient).when(hostClientFactory).create();
  }

  public static void mockHttpFileServiceClient(HttpFileServiceClientFactory httpFileServiceClientFactory,
                                               boolean isSuccess) throws Throwable {
    HttpFileServiceClient httpFileServiceClient = mock(HttpFileServiceClient.class);
    if (isSuccess) {
      when(httpFileServiceClient.uploadFile(anyString(), anyString(), anyBoolean())).thenReturn(() -> 201);
      when(httpFileServiceClient.uploadFileToDatastore(anyString(), anyString(), anyString())).thenReturn(() -> 201);
      when(httpFileServiceClient.deleteFileFromDatastore(anyString(), anyString())).thenReturn(() -> 204);
      when(httpFileServiceClient.getDirectoryListingOfDatastores()).thenReturn(() -> 200);
    } else {
      when(httpFileServiceClient.uploadFile(anyString(), anyString(), anyBoolean())).thenThrow(
          new RuntimeException(new IOException("Copy failed")));
      when(httpFileServiceClient.uploadFileToDatastore(anyString(), anyString(), anyString())).thenThrow(
          new RuntimeException(new IOException("Copy failed")));
      when(httpFileServiceClient.deleteFileFromDatastore(anyString(), anyString())).thenThrow(
          new RuntimeException("Delete failed"));
      when(httpFileServiceClient.getDirectoryListingOfDatastores()).thenThrow(new RuntimeException("Login failed"));
    }

    doReturn(httpFileServiceClient).when(httpFileServiceClientFactory).create(anyString(), anyString(), anyString());
  }

  public static Callable<Integer> mockUploadFile(int returnCode) {
    return () -> returnCode;
  }

  public static void mockCreateContainer(DockerProvisionerFactory dockerProvisionerFactory, boolean isSuccess) throws
      Throwable {
    DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
    when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
    if (isSuccess) {
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), anyBoolean(),
          Matchers.<String>anyVararg())).thenReturn("id");
    } else {
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), anyBoolean(),
          Matchers.<String>anyVararg())).thenThrow(new DockerException("Start container " + "failed", 500));
    }
  }

  public static void mockAuthHelper(AuthClientHandler.ImplicitClient implicitClient,
                                    AuthHelperFactory authHelperFactory, boolean isSuccess) throws Throwable {
    AuthHelper authHelper = mock(AuthHelper.class);
    doReturn(authHelper).when(authHelperFactory).create();

    if (isSuccess) {
      doReturn(implicitClient).when(authHelper).getResourceLoginUri(anyString(), anyString(), anyString(),
          anyString(), anyInt(), anyString(), anyString());
    } else {
      doThrow(new AuthException("Failed to obtain the resource login url"))
          .when(authHelper)
          .getResourceLoginUri(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyString());
    }
  }

  public static void mockHealthChecker(HealthCheckHelperFactory healthCheckHelperFactory, final boolean isSuccess)
      throws Throwable {
    HealthChecker healthChecker = new HealthChecker() {
      @Override
      public boolean isReady() {
        return isSuccess;
      }
    };
    HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);
    PowerMockito.when(healthCheckHelper.getHealthChecker()).thenReturn(healthChecker);

    when(healthCheckHelperFactory.create(
        any(Service.class), any(ContainersConfig.ContainerType.class), anyString())).thenReturn(healthCheckHelper);
  }

  public static void mockCreateScriptFile(DeployerContext deployerContext, String scriptName, boolean isSuccess) throws
      Throwable {

    if (isSuccess) {
      TestHelper.createSuccessScriptFile(
          deployerContext,
          scriptName);
    } else {
      TestHelper.createFailScriptFile(
          deployerContext,
          scriptName);
    }
  }

  public static HostClient mockProvisionAgent(AgentControlClientFactory agentControlClientFactory,
                                              HostClientFactory hostClientFactory,
                                              boolean isSuccess) {
    AgentControlClient agentControlClient;
    HostClient hostClient;
    if (isSuccess) {
      agentControlClient = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .build();
      hostClient = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.OK)
          .build();
    } else {
      agentControlClient = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.SYSTEM_ERROR)
          .agentStatusCode(AgentStatusCode.UPGRADING)
          .build();
      hostClient = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.SYSTEM_ERROR)
          .build();
    }
    doReturn(agentControlClient).when(agentControlClientFactory).create();
    doReturn(hostClient).when(hostClientFactory).create();
    return hostClient;
  }

  @SuppressWarnings("unchecked")
  public static Answer<AsyncMethodCallback<AgentControl.AsyncClient.provision_call>> mockProvisionAgent(
      ProvisionResultCode resultCode) {

    return (invocation) -> {
      AsyncMethodCallback<AgentControl.AsyncClient.provision_call> callback =
          ((AsyncMethodCallback<AgentControl.AsyncClient.provision_call>) invocation.getArguments()[14]);
      AgentControl.AsyncClient.provision_call provisionCall = mock(AgentControl.AsyncClient.provision_call.class);
      ProvisionResponse provisionResponse = new ProvisionResponse(resultCode);
      doReturn(provisionResponse).when(provisionCall).getResult();
      callback.onComplete(provisionCall);
      return null;
    };
  }

  @SuppressWarnings("unchecked")
  public static Answer<AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call>> mockGetAgentStatus(
      AgentStatusCode agentStatusCode) {

    return (invocation) -> {
      AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call> callback =
          ((AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call>) invocation.getArguments()[0]);
      AgentControl.AsyncClient.get_agent_status_call getAgentStatusCall =
          mock(AgentControl.AsyncClient.get_agent_status_call.class);
      AgentStatusResponse agentStatusResponse = new AgentStatusResponse(agentStatusCode);
      doReturn(agentStatusResponse).when(getAgentStatusCall).getResult();
      callback.onComplete(getAgentStatusCall);
      return null;
    };
  }

  @SuppressWarnings("unchecked")
  public static Answer<FutureCallback<FabricNode>> mockRegisterFabricNode(String fabricNodeId) {
    return (invocation) -> {
      FutureCallback<FabricNode> callback = ((FutureCallback<FabricNode>) invocation.getArguments()[1]);
      FabricNode fabricNode = new FabricNode();
      fabricNode.setId(fabricNodeId);
      callback.onSuccess(fabricNode);
      return null;
    };
  }

  @SuppressWarnings("unchecked")
  public static Answer<FutureCallback<FabricNodeState>> mockGetFabricNodeState(
      com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState state) {

    return (invocation) -> {
      FutureCallback<FabricNodeState> callback = ((FutureCallback<FabricNodeState>) invocation.getArguments()[1]);
      FabricNodeState fabricNodeState = new FabricNodeState();
      fabricNodeState.setState(state);
      callback.onSuccess(fabricNodeState);
      return null;
    };
  }

  @SuppressWarnings("unchecked")
  public static Answer<FutureCallback<TransportNode>> mockCreateTransportNode(String transportNodeId) {
    return (invocation) -> {
      FutureCallback<TransportNode> callback = ((FutureCallback<TransportNode>) invocation.getArguments()[1]);
      TransportNode transportNode = new TransportNode();
      transportNode.setId(transportNodeId);
      callback.onSuccess(transportNode);
      return null;
    };
  }

  @SuppressWarnings("unchecked")
  public static Answer<FutureCallback<TransportNodeState>> mockGetTransportNodeState(
      com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState state) {

    return (invocation) -> {
      FutureCallback<TransportNodeState> callback = ((FutureCallback<TransportNodeState>) invocation.getArguments()[1]);
      TransportNodeState transportNodeState = new TransportNodeState();
      transportNodeState.setState(state);
      callback.onSuccess(transportNodeState);
      return null;
    };
  }

  @SuppressWarnings("unchecked")
  public static Answer<AsyncMethodCallback<Host.AsyncClient.get_host_config_call>> mockGetHostConfig(
      List<String> datastoreList, List<String> networkList, String esxVersion) {

    return (invocation) -> {
      AsyncMethodCallback<Host.AsyncClient.get_host_config_call> callback =
          ((AsyncMethodCallback<Host.AsyncClient.get_host_config_call>) invocation.getArguments()[0]);

      HostConfig hostConfig = new HostConfig();
      hostConfig.setDatastores(datastoreList.stream().map(Datastore::new).collect(Collectors.toList()));
      hostConfig.setNetworks(networkList.stream().map(Network::new).collect(Collectors.toList()));
      hostConfig.setEsx_version(esxVersion);

      GetConfigResponse getConfigResponse = new GetConfigResponse(GetConfigResultCode.OK);
      getConfigResponse.setHostConfig(hostConfig);
      Host.AsyncClient.get_host_config_call getHostConfigCall = mock(Host.AsyncClient.get_host_config_call.class);
      doReturn(getConfigResponse).when(getHostConfigCall).getResult();
      callback.onComplete(getHostConfigCall);
      return null;
    };
  }

  @SuppressWarnings("unchecked")
  public static Answer<AsyncMethodCallback<Host.AsyncClient.get_host_config_call>> mockGetHostConfig(
      GetConfigResultCode resultCode) {

    return (invocation) -> {
      AsyncMethodCallback<Host.AsyncClient.get_host_config_call> callback =
          ((AsyncMethodCallback<Host.AsyncClient.get_host_config_call>) invocation.getArguments()[0]);

      GetConfigResponse getConfigResponse = new GetConfigResponse(resultCode);
      Host.AsyncClient.get_host_config_call getHostConfigCall = mock(Host.AsyncClient.get_host_config_call.class);
      doReturn(getConfigResponse).when(getHostConfigCall).getResult();
      callback.onComplete(getHostConfigCall);
      return null;
    };
  }

  public static StaticServerSet mockCloudStoreServerSet() throws Throwable {
    BasicServiceHost host = BasicServiceHost.create(
        null,
        DatastoreServiceFactory.SELF_LINK,
        10, 10);

    host.startServiceSynchronously(new DatastoreServiceFactory(), null);

    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    return serverSet;
  }

  public static Answer<Task> mockCreateFlavorAsync(String taskId, String entityId, String state) {
    return mockCreateFlavorAsync(TestHelper.createTask(taskId, entityId, state));
  }

  public static Answer<Task> mockCreateFlavorAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(returnValue);
      return null;
    };
  }

  public static Answer<Task> mockCreateProjectAsync(String taskId, String entityId, String state) {
    return mockCreateProjectAsync(TestHelper.createTask(taskId, entityId, state));
  }

  public static Answer<Task> mockCreateProjectAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(returnValue);
      return null;
    };
  }

  public static Answer<Task> mockCreateResourceTicketAsync(String taskId, String entityId, String state) {
    return mockCreateResourceTicketAsync(TestHelper.createTask(taskId, entityId, state));
  }

  public static Answer<Task> mockCreateResourceTicketAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(returnValue);
      return null;
    };
  }

  public static Answer<Task> mockCreateTenantAsync(String taskId, String entityId, String state) {
    return mockCreateTenantAsync(TestHelper.createTask(taskId, entityId, state));
  }

  public static Answer<Task> mockCreateTenantAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(returnValue);
      return null;
    };
  }

  public static Answer<Task> mockCreateVmAsync(String taskId, String entityId, String state) {
    return mockCreateVmAsync(TestHelper.createTask(taskId, entityId, state));
  }

  @SuppressWarnings("unchecked")
  public static Answer<Task> mockCreateVmAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(returnValue);
      return null;
    };
  }

  public static Answer<Image> mockGetImageAsync(String imageId, String imageSeedingProgress) {
    return mockGetImageAsync(TestHelper.createImage(imageId, imageSeedingProgress));
  }

  public static Answer<Image> mockGetImageAsync(String imageId, ImageState imageState) {
    return mockGetImageAsync(TestHelper.createImage(imageId, imageState));
  }

  @SuppressWarnings("unchecked")
  public static Answer<Image> mockGetImageAsync(Image returnValue) {
    return (invocation) -> {
      ((FutureCallback<Image>) invocation.getArguments()[1]).onSuccess(returnValue);
      return null;
    };
  }


  public static Answer<Task> mockPerformStartOperationAsync(String taskId, String entityId, String state) {
    return mockPerformStartOperationAsync(TestHelper.createTask(taskId, entityId, state));
  }

  @SuppressWarnings("unchecked")
  public static Answer<Task> mockPerformStartOperationAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(returnValue);
      return null;
    };
  }

  public static Answer<Task> mockGetTaskAsync(String taskId, String entityId, String state) {
    return mockGetTaskAsync(TestHelper.createTask(taskId, entityId, state));
  }

  public static Answer<Task> mockGetTaskAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(returnValue);
      return null;
    };
  }

  public static Answer<Task> mockSetMetadataAsync(String taskId, String entityId, String state) {
    return mockSetMetadataAsync(TestHelper.createTask(taskId, entityId, state));
  }

  @SuppressWarnings("unchecked")
  public static Answer<Task> mockSetMetadataAsync(Task returnValue) {
    return (invocation) -> {
      ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(returnValue);
      return null;
    };
  }

  public static void mockApiClient(ApiClientFactory apiClientFactory, MultiHostEnvironment<?> machine, boolean
      isSuccess) throws Throwable {
    ApiClient apiClient = mock(ApiClient.class);
    ProjectApi projectApi = mock(ProjectApi.class);
    TasksApi tasksApi = mock(TasksApi.class);
    VmApi vmApi = mock(VmApi.class);
    FlavorApi flavorApi = mock(FlavorApi.class);
    ImagesApi imagesApi = mock(ImagesApi.class);
    TenantsApi tenantsApi = mock(TenantsApi.class);

    final Task taskReturnedByCreateVm = TestHelper.createCompletedApifeTask("CREATE_VM");
    final Task taskReturnedBySetMetadata = TestHelper.createCompletedApifeTask("SET_METADATA");
    final Task taskReturnedByAttachIso = TestHelper.createCompletedApifeTask("ATTACH_ISO");
    final Task taskReturnedByCreateManagementVmFlavor =
        TestHelper.createCompletedApifeTask("CREATE_MANAGEMENT_VM_FLAVOR");
    Task.Entity taskEntity = new Task.Entity();
    taskEntity.setId("VM_FLAVOR_ID");
    taskReturnedByCreateManagementVmFlavor.setEntity(taskEntity);

    final Task taskReturnedByCreateManagementVmDiskFlavor =
        TestHelper.createCompletedApifeTask("CREATE_MANAGEMENT_VM_DISK_FLAVOR");
    Task.Entity diskTaskEntity = new Task.Entity();
    diskTaskEntity.setId("DISK_FLAVOR_ID");
    taskReturnedByCreateManagementVmDiskFlavor.setEntity(diskTaskEntity);

    final Task taskReturnedByCreateClusterMasterVmFlavor =
        TestHelper.createCompletedApifeTask("CREATE_CLUSTER_MASTER_VM_FLAVOR");
    Task.Entity clusterVmTaskEntity = new Task.Entity();
    clusterVmTaskEntity.setId("CLUSTER_VM_FLAVOR_ID");
    taskReturnedByCreateClusterMasterVmFlavor.setEntity(clusterVmTaskEntity);
    final Task taskReturnedByCreateClusterVmDiskFlavor =
        TestHelper.createCompletedApifeTask("CREATE_CLUSTER_VM_DISK_FLAVOR");

    Task.Entity taskEntityImage = new Task.Entity();
    taskEntityImage.setId("IMAGE_ID");
    Task taskReturnedByUploadManagementImage = new Task();
    taskReturnedByUploadManagementImage.setId("taskId");
    taskReturnedByUploadManagementImage.setState("COMPLETED");
    taskReturnedByUploadManagementImage.setEntity(taskEntityImage);

    Task.Entity clusterVmDiskTaskEntity = new Task.Entity();
    clusterVmDiskTaskEntity.setId("CLUSTER_DISK_FLAVOR_ID");
    taskReturnedByCreateClusterVmDiskFlavor.setEntity(clusterVmDiskTaskEntity);

    TenantService.State tenantState = TestHelper.createTenant(machine);
    String tenantId = ServiceUtils.getIDFromDocumentSelfLink(tenantState.documentSelfLink);
    ResourceTicketService.State resourceTicketState = TestHelper.createResourceTicket(tenantId, machine);
    final Task taskReturnedByCreateTenant = TestHelper.createCompletedApifeTask("CREATE_TENANT", tenantId);
    final Task taskReturnedByCreateResourceTicket = TestHelper.createCompletedApifeTask("CREATE_RESOURCE_TICKET",
        ServiceUtils.getIDFromDocumentSelfLink(resourceTicketState.documentSelfLink));
    final Task taskReturnedByCreateProject = TestHelper.createCompletedApifeTask("CREATE_PROJECT", "PROJECT_ID");
    final Task taskReturnedByPerformVmOperation = TestHelper.createCompletedApifeTask("PERFORM_VM_OPERATION");
    final Image imageReturnedByGetImageAsync = TestHelper.createImage("IMAGE_ID", "100.0%");

    if (isSuccess) {
      // Create project
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateProject);
          return null;
        }
      }).when(tenantsApi).createProjectAsync(
          any(String.class), any(ProjectCreateSpec.class), any(FutureCallback.class));

      // Create resource ticket
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateResourceTicket);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(
          any(String.class), any(ResourceTicketCreateSpec.class), any(FutureCallback.class));

      // Create tenant
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateTenant);
          return null;
        }
      }).when(tenantsApi).createAsync(any(String.class), any(FutureCallback.class));

      // Create management VM flavor
      ArgumentMatcher<FlavorCreateSpec> managementVmFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().startsWith("mgmt-vm-ec-mgmt");
        }
      };

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateManagementVmFlavor);
          return null;
        }
      }).when(flavorApi).createAsync(argThat(managementVmFlavorSpecMatcher), any(FutureCallback.class));

      // Create cluster VM flavor
      ArgumentMatcher<FlavorCreateSpec> clusterMasterVmFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().startsWith("cluster-master-vm");
        }
      };

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateClusterMasterVmFlavor);
          return null;
        }
      }).when(flavorApi).createAsync(argThat(clusterMasterVmFlavorSpecMatcher), any(FutureCallback.class));

      ArgumentMatcher<FlavorCreateSpec> clusterOtherVmFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().startsWith("cluster-other-vm");
        }
      };

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateClusterMasterVmFlavor);
          return null;
        }
      }).when(flavorApi).createAsync(argThat(clusterOtherVmFlavorSpecMatcher), any(FutureCallback.class));

      // Create management VM disk flavor
      ArgumentMatcher<FlavorCreateSpec> managementVmDiskFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().startsWith("mgmt-vm-disk-ec-mgmt");
        }
      };

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateManagementVmDiskFlavor);
          return null;
        }
      }).when(flavorApi).createAsync(argThat(managementVmDiskFlavorSpecMatcher), any(FutureCallback.class));

      // Create cluster VM disk flavor
      ArgumentMatcher<FlavorCreateSpec> clusterVmDiskFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().startsWith("cluster-vm-disk");
        }
      };

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateClusterVmDiskFlavor);
          return null;
        }
      }).when(flavorApi).createAsync(argThat(clusterVmDiskFlavorSpecMatcher), any(FutureCallback.class));


      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByPerformVmOperation);
          return null;
        }
      }).when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback.class));

      // Upload and attach ISO
      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());

      // Upload image
      doReturn(taskReturnedByUploadManagementImage).
          when(imagesApi).uploadImage(any(FileBody.class), anyString());
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Image>) invocation.getArguments()[1]).onSuccess(imageReturnedByGetImageAsync);
          return null;
        }
      }).when(imagesApi).getImageAsync(anyString(), any(FutureCallback.class));

      // Create VM
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      // Set metadata
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedBySetMetadata);
          return null;
        }
      }).when(vmApi).setMetadataAsync(any(String.class), any(VmMetadata.class), any(FutureCallback.class));
    }

    doReturn(projectApi).when(apiClient).getProjectApi();
    doReturn(tasksApi).when(apiClient).getTasksApi();
    doReturn(vmApi).when(apiClient).getVmApi();
    doReturn(flavorApi).when(apiClient).getFlavorApi();
    doReturn(imagesApi).when(apiClient).getImagesApi();
    doReturn(tenantsApi).when(apiClient).getTenantsApi();
    doReturn(apiClient).when(apiClientFactory).create();
    doReturn(apiClient).when(apiClientFactory).create(any(String.class));
  }

  public static void mockServiceConfigurator(ServiceConfiguratorFactory serviceConfiguratorFactory, boolean
      isSuccess) throws Throwable {
    ServiceConfigurator serviceConfigurator = mock(ServiceConfigurator.class);
    doReturn(serviceConfigurator).when(serviceConfiguratorFactory).create();
    doNothing().when(serviceConfigurator).copyDirectory(any(String.class), any(String.class));
    if (isSuccess) {
      doNothing().when(serviceConfigurator).applyDynamicParameters(any(String.class), any(ContainersConfig
          .ContainerType.class), any(Map.class));
    } else {
      doNothing().doThrow(new RuntimeException("Config not available")).when(serviceConfigurator).
          applyDynamicParameters(any(String.class), any(ContainersConfig.ContainerType.class), any(Map.class));
    }
  }
}
