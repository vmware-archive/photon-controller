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

package com.vmware.photon.controller.deployer.helpers;

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantServiceFactory;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.MultiHostEnvironment;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.DeployerConfigTest;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DcpConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * This class implements helper routines for tests.
 */
public class TestHelper {
  public static Injector createInjector(String configFileResourcePath)
      throws BadConfigException {
    DeployerConfig config = ConfigBuilder.build(DeployerConfig.class,
        DeployerConfigTest.class.getResource(configFileResourcePath).getPath());
    return Guice.createInjector(
        new ZookeeperModule(),
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        ),
        new TestDeployerModule(config));
  }

  /**
   * Class for constructing config injection.
   */
  public static class TestInjectedConfig {
    private String bind;
    private String registrationAddress;
    private int port;
    private String path;

    @Inject
    public TestInjectedConfig(
        @DeployerConfig.Bind String bind,
        @DeployerConfig.RegistrationAddress String registrationAddress,
        @DeployerConfig.Port int port,
        @DcpConfig.StoragePath String path) {
      this.bind = bind;
      this.registrationAddress = registrationAddress;
      this.port = port;
      this.path = path;
    }

    public String getBind() {
      return bind;
    }

    public String getRegistrationAddress() {
      return registrationAddress;
    }

    public int getPort() {
      return port;
    }

    public String getPath() {
      return path;
    }
  }

  //
  // Start state routines
  //

  public static ContainerService.State getContainerServiceStartState() {
    ContainerService.State startState = new ContainerService.State();
    startState.containerTemplateServiceLink = "CONTAINER_TEMPLATE_SERVICE_LINK";
    startState.vmServiceLink = "VM_SERVICE_LINK";
    return startState;
  }

  public static ContainerService.State getContainerServiceStartState(VmService.State vmServiceState) {
    ContainerService.State startState = new ContainerService.State();
    startState.containerTemplateServiceLink = "CONTAINER_TEMPLATE_SERVICE_LINK";
    startState.vmServiceLink = vmServiceState.documentSelfLink;
    return startState;
  }

  public static ContainerService.State getContainerServiceStartState(
      ContainerTemplateService.State containerTemplateState, VmService.State vmServiceState) {
    ContainerService.State startState = new ContainerService.State();
    startState.containerTemplateServiceLink = containerTemplateState.documentSelfLink;
    startState.vmServiceLink = vmServiceState.documentSelfLink;
    return startState;
  }

  public static ContainerTemplateService.State getContainerTemplateServiceStartState() {
    ContainerTemplateService.State startState = new ContainerTemplateService.State();
    startState.name = "NAME";
    startState.isReplicated = false;
    startState.containerImage = "IMAGE";
    startState.isPrivileged = false;
    startState.environmentVariables = new HashMap<>();
    startState.portBindings = new HashMap<>();
    startState.portBindings.put(5432, 5432);
    startState.cpuCount = 1;
    startState.memoryMb = 2048L;
    startState.diskGb = 4;
    return startState;
  }

  public static ContainerTemplateService.State getContainerTemplateServiceStartState(
      ContainersConfig.ContainerType containerType) {
    ContainerTemplateService.State containerTemplateStartState = getContainerTemplateServiceStartState();
    containerTemplateStartState.name = containerType.name();
    return containerTemplateStartState;
  }

  public static ContainerTemplateService.State getContainerTemplateServiceStartState(ContainersConfig.Spec spec) {
    ContainerTemplateService.State containerTemplateServiceState = new ContainerTemplateService.State();
    containerTemplateServiceState.name = spec.getType();
    containerTemplateServiceState.isReplicated = spec.getIsReplicated();
    containerTemplateServiceState.cpuCount = spec.getCpuCount();
    containerTemplateServiceState.memoryMb = spec.getMemoryMb();
    containerTemplateServiceState.diskGb = spec.getDiskGb();
    containerTemplateServiceState.isPrivileged = spec.getIsPrivileged();
    containerTemplateServiceState.volumesFrom = spec.getVolumesFrom();
    containerTemplateServiceState.containerImage = spec.getContainerImage();

    containerTemplateServiceState.portBindings = new HashMap<>();
    if (null != spec.getPortBindings()) {
      containerTemplateServiceState.portBindings.putAll(spec.getPortBindings());
    }

    containerTemplateServiceState.volumeBindings = new HashMap<>();
    if (null != spec.getVolumeBindings()) {
      containerTemplateServiceState.volumeBindings.putAll(spec.getVolumeBindings());
    }

    containerTemplateServiceState.environmentVariables = new HashMap<>();
    if (null != spec.getDynamicParameters()) {
      containerTemplateServiceState.environmentVariables.putAll(spec.getDynamicParameters());
    }

    return containerTemplateServiceState;
  }

  public static DeploymentService.State getDeploymentServiceStartState(boolean authEnabled) {
    DeploymentService.State startState = new DeploymentService.State();
    startState.imageDataStoreNames = Collections.singleton("IMAGE_DATASTORE_NAME");
    startState.imageDataStoreUsedForVMs = true;
    startState.state = DeploymentState.READY;
    startState.ntpEndpoint = "NTP_ENDPOINT";
    startState.syslogEndpoint = "SYSLOG_ENDPOINT";
    startState.oAuthEnabled = authEnabled;
    startState.oAuthServerAddress = "OAUTH_ENDPOINT";
    startState.chairmanServerList = Collections.singleton("127.0.0.1:13000");
    if (startState.oAuthEnabled) {
      startState.oAuthServerPort = 433;
    } else {
      startState.oAuthServerPort = 500;
    }
    return startState;
  }

  public static HostService.State getHostServiceStartState(Set<String> usageTags, HostState state) {
    HostService.State startState = new HostService.State();
    startState.state = state;
    startState.hostAddress = "hostAddress";
    startState.userName = "userName";
    startState.password = "password";
    startState.availabilityZoneId = "availabilityZone";
    startState.esxVersion = "6.0";
    startState.usageTags = new HashSet<>(usageTags);

    if (usageTags.contains(UsageTag.MGMT.name())) {
      startState.metadata = new HashMap<>();
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE, "datastore1");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER, "8.8.8.8");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY, "8.8.8.143");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "8.8.8.27");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK, "255.255.255.0");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP, "VM Network");
    }

    return startState;
  }

  public static VmService.State getVmServiceStartState() {
    VmService.State startState = new VmService.State();
    startState.name = "NAME";
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    return startState;
  }

  public static VmService.State getVmServiceStartState(HostService.State hostState) {
    VmService.State startState = new VmService.State();
    startState.name = "NAME";
    startState.hostServiceLink = hostState.documentSelfLink;
    return startState;
  }

  public static VmService.State getVmServiceStartState(HostService.State hostService, String vmId) {
    VmService.State startState = getVmServiceStartState(hostService);
    startState.vmId = vmId;
    return startState;
  }

  //
  // Test state setup routines
  //

  public static ContainerService.State createContainerService(
      TestEnvironment testEnvironment) throws Throwable {
    return createContainerService(testEnvironment, getContainerServiceStartState());
  }

  public static ContainerService.State createContainerService(
      TestEnvironment testEnvironment, VmService.State vmState) throws Throwable {
    return createContainerService(testEnvironment, getContainerServiceStartState(vmState));
  }

  public static ContainerService.State createContainerService(
      TestEnvironment testEnvironment, ContainerTemplateService.State containerTemplateState, VmService.State vmState)
      throws Throwable {
    return createContainerService(testEnvironment, getContainerServiceStartState(containerTemplateState, vmState));
  }

  public static ContainerService.State createContainerService(
      TestEnvironment testEnvironment, ContainerService.State startState) throws Throwable {
    return testEnvironment.callServiceSynchronously(
        ContainerFactoryService.SELF_LINK,
        startState,
        ContainerService.State.class);
  }

  public static ContainerTemplateService.State createContainerTemplateService(
      TestEnvironment testEnvironment) throws Throwable {
    return createContainerTemplateService(testEnvironment, getContainerTemplateServiceStartState());
  }

  public static ContainerTemplateService.State createContainerTemplateService(
      TestEnvironment testEnvironment, ContainersConfig.ContainerType containerType) throws Throwable {
    return createContainerTemplateService(testEnvironment, getContainerTemplateServiceStartState(containerType));
  }

  public static ContainerTemplateService.State createContainerTemplateService(
      TestEnvironment testEnvironment, ContainersConfig.Spec spec) throws Throwable {
    return createContainerTemplateService(testEnvironment, getContainerTemplateServiceStartState(spec));
  }

  public static ContainerTemplateService.State createContainerTemplateService(
      TestEnvironment testEnvironment, ContainerTemplateService.State startState) throws Throwable {
    return testEnvironment.callServiceSynchronously(
        ContainerTemplateFactoryService.SELF_LINK,
        startState,
        ContainerTemplateService.State.class);
  }

  public static DeploymentService.State createDeploymentService(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment) throws
      Throwable {
    return createDeploymentService(testEnvironment, false);
  }

  public static DeploymentService.State createDeploymentService(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment, boolean isAuthEnabled) throws
      Throwable {
    return createDeploymentService(testEnvironment, getDeploymentServiceStartState(isAuthEnabled));
  }

  public static DeploymentService.State createDeploymentService(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment,
      DeploymentService.State startState) throws Throwable {

    return testEnvironment.callServiceSynchronously(
        DeploymentServiceFactory.SELF_LINK,
        startState,
        DeploymentService.State.class);
  }

  public static HostService.State createHostService(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment, Set<String> usageTags)
      throws Throwable {
    return createHostService(testEnvironment, usageTags, HostState.READY);
  }

  public static HostService.State createHostService(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment, Set<String> usageTags,
      HostState state) throws Throwable {
    return createHostService(testEnvironment, getHostServiceStartState(usageTags, state));
  }

  public static HostService.State createHostService(MultiHostEnvironment testEnvironment, HostService.State startState)
      throws Throwable {
    return (HostService.State) testEnvironment.callServiceSynchronously(
        HostServiceFactory.SELF_LINK,
        startState,
        HostService.State.class);
  }

  public static VmService.State createVmService(
      TestEnvironment testEnvironment) throws Throwable {
    return createVmService(testEnvironment, getVmServiceStartState());
  }

  public static VmService.State createVmService(
      TestEnvironment testEnvironment, HostService.State hostState) throws Throwable {
    return createVmService(testEnvironment, getVmServiceStartState(hostState));
  }

  public static VmService.State createVmService(
      TestEnvironment testEnvironment, HostService.State hostService, String vmId) throws Throwable {
    return createVmService(testEnvironment, getVmServiceStartState(hostService, vmId));
  }

  public static VmService.State createVmService(
      TestEnvironment testEnvironment, VmService.State startState) throws Throwable {
    return testEnvironment.callServiceSynchronously(
        VmFactoryService.SELF_LINK,
        startState,
        VmService.State.class);
  }

  public static ImageService.State createImageService(MultiHostEnvironment<?> cloudStoreMachine) throws Throwable {
    ImageService.State imageServiceStartState = new ImageService.State();
    imageServiceStartState.name = "imageName";
    imageServiceStartState.replicationType = ImageReplicationType.EAGER;
    imageServiceStartState.state = ImageState.READY;
    ImageService.State imageServiceState =
        cloudStoreMachine.callServiceSynchronously(
            ImageServiceFactory.SELF_LINK,
            imageServiceStartState,
            ImageService.State.class);
    return imageServiceState;
  }

  public static FlavorService.State createFlavor(MultiHostEnvironment<?> cloudStoreMachine, String name) throws
      Throwable {
    FlavorService.State flavorServiceStartState = new FlavorService.State();
    if (name == null) {
      flavorServiceStartState.name = "dummyName";
    } else {
      flavorServiceStartState.name = name;
    }
    flavorServiceStartState.kind = "dummyKind";
    flavorServiceStartState.cost = new ArrayList<>();
    flavorServiceStartState.tags = new HashSet<>();
    flavorServiceStartState.state = FlavorState.READY;
    FlavorService.State flavorServiceState = cloudStoreMachine.callServiceSynchronously(
        FlavorServiceFactory.SELF_LINK,
        flavorServiceStartState,
        FlavorService.State.class);
    return flavorServiceState;
  }

  public static TenantService.State createTenant(MultiHostEnvironment<?> cloudStoreMachine) throws Throwable {
    TenantService.State tenantServiceStartState = new TenantService.State();
    tenantServiceStartState.name = Constants.TENANT_NAME;
    TenantService.State tenantState =
        cloudStoreMachine.callServiceSynchronously(
            TenantServiceFactory.SELF_LINK,
            tenantServiceStartState,
            TenantService.State.class);
    return tenantState;
  }

  public static ResourceTicketService.State createResourceTicket(String tenantId, MultiHostEnvironment<?>
      cloudStoreMachine) throws Throwable {
    ResourceTicketService.State resourceTicketServiceStartState = new ResourceTicketService.State();
    resourceTicketServiceStartState.tenantId = tenantId;
    resourceTicketServiceStartState.name = Constants.RESOURCE_TICKET_NAME;
    ResourceTicketService.State resourceTicketState =
        cloudStoreMachine.callServiceSynchronously(
            ResourceTicketServiceFactory.SELF_LINK,
            resourceTicketServiceStartState,
            ResourceTicketService.State.class);
    return resourceTicketState;
  }

  public static ProjectService.State createProject(String tenantId, String resourceTicketId, MultiHostEnvironment<?>
      cloudStoreMachine) throws Throwable {
    ProjectService.State projectServiceStartState = new ProjectService.State();
    projectServiceStartState.name = Constants.PROJECT_NAME;
    projectServiceStartState.resourceTicketId = resourceTicketId;
    projectServiceStartState.tenantId = tenantId;
    ProjectService.State projectState =
        cloudStoreMachine.callServiceSynchronously(
            ProjectServiceFactory.SELF_LINK,
            projectServiceStartState,
            ProjectService.State.class);
    return projectState;
  }

  //
  // Test validation and cleanup routines.
  //

  public static <T extends ServiceDocument> List<String> getServiceLinksOfType(
      MultiHostEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create().addKindFieldClause(clazz).build())
        .build();

    return testEnvironment.sendQueryAndWait(queryTask).results.documentLinks;
  }

  public static <T extends ServiceDocument> List<T> getServicesOfType(
      MultiHostEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    QueryTask queryResult = testEnvironment.sendQueryAndWait(QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create().addKindFieldClause(clazz).build())
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
        .build());

    List<T> returnValue = new ArrayList<>(queryResult.results.documentLinks.size());
    for (String documentLink : queryResult.results.documentLinks) {
      returnValue.add(Utils.fromJson(queryResult.results.documents.get(documentLink), clazz));
    }

    return returnValue;
  }

  public static <T extends ServiceDocument> void assertNoServicesOfType(
      MultiHostEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    List<String> documentLinks = getServiceLinksOfType(testEnvironment, clazz);
    assertThat(documentLinks.size(), is(0));
  }

  public static <T extends ServiceDocument> void deleteServicesOfType(
      MultiHostEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    List<String> documentLinks = getServiceLinksOfType(testEnvironment, clazz);
    if (documentLinks.size() > 0) {
      for (String documentLink : documentLinks) {
        testEnvironment.sendDeleteAndWait(documentLink);
      }
    }
  }

  //
  // Mocking helper routines
  //

  public static void createSuccessScriptFile(DeployerContext deployerContext, String fileName) throws IOException {
    createScriptFile(deployerContext, "exit 0\n", fileName);
  }

  public static void createFailScriptFile(DeployerContext deployerContext, String fileName) throws IOException {
    createScriptFile(deployerContext, "exit 1\n", fileName);
  }

  public static void createScriptFile(
      DeployerContext deployerContext,
      String scriptContents,
      String scriptFileName)
      throws IOException {
    File scriptFile = new File(deployerContext.getScriptDirectory(), scriptFileName);
    scriptFile.createNewFile();
    scriptFile.setExecutable(true, true);
    FileUtils.writeStringToFile(scriptFile, scriptContents);
  }

  public static File createSourceFile(String sourceFileName, File sourceDirectory) throws IOException {
    sourceDirectory.mkdirs();

    if (sourceFileName == null) {
      sourceFileName = "esxcloud-" + UUID.randomUUID().toString() + ".vib";
    }

    File sourceFile = new File(sourceDirectory, sourceFileName);
    sourceFile.createNewFile();
    OutputStream outputStream = new FileOutputStream(sourceFile);
    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

    Random random = new Random();
    byte[] randomBytes = new byte[1024];
    for (int i = 0; i < 10 * 1024; i++) {
      random.nextBytes(randomBytes);
      bufferedOutputStream.write(randomBytes);
    }

    bufferedOutputStream.close();
    outputStream.close();
    return sourceFile;
  }

  public static Task createCompletedApifeTask(String taskName) {
    String entityId = String.format("%s_ENTITY_ID", taskName);
    return createCompletedApifeTask(taskName, entityId);
  }

  public static Task createCompletedApifeTask(String taskName, String entityId) {
    String taskId = String.format("%s_TASK_ID", taskName);
    return createTask(taskId, entityId, "COMPLETED");
  }

  public static Task createTask(String taskName, String state) {
    String entityId = String.format("%s_ENTITY_ID", taskName);
    String taskId = String.format("%s_TASK_ID", taskName);
    return createTask(taskId, entityId, state);
  }

  public static Task createTask(String taskId, String entityId, String state) {

    Task.Entity entity = new Task.Entity();
    entity.setId(entityId);

    Task task = new Task();
    task.setId(taskId);
    task.setState(state);
    task.setEntity(entity);

    return task;
  }

  //
  // Utility routines
  //

  public static Object[][] toDataProvidersList(List<?> list) {
    Object[][] objects = new Object[list.size()][1];
    for (int i = 0; i < list.size(); ++i) {
      objects[i][0] = list.get(i);
    }
    return objects;
  }

  public static void assertTaskStateFinished(TaskState taskState) {
    assertThat(
        String.format("Unexpected task stage result %s (message: %s, stack trace: %s)",
            taskState.stage,
            null != taskState.failure ? taskState.failure.message : "null",
            null != taskState.failure ? taskState.failure.stackTrace : "null"),
        taskState.stage,
        is(TaskState.TaskStage.FINISHED));
  }

  public static void setContainersConfig(DeployerConfig deployerConfig) {
    deployerConfig.setContainersConfig(new ServiceConfigurator().generateContainersConfig(TestHelper.class
        .getResource("/configurations/").getPath()));
  }
}
