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

package com.vmware.photon.controller.apife.commands.tasks;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.DcpBackendTestHelper;
import com.vmware.photon.controller.apife.backends.DcpBackendTestModule;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.FlavorDcpBackend;
import com.vmware.photon.controller.apife.backends.FlavorLoader;
import com.vmware.photon.controller.apife.backends.ProjectDcpBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketDcpBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TenantDcpBackend;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.CommandTestModule;
import com.vmware.photon.controller.apife.commands.steps.StepCommand;
import com.vmware.photon.controller.apife.commands.steps.StepCommandFactory;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.VmService;
import com.vmware.photon.controller.cloudstore.dcp.entity.VmServiceFactory;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.RootSchedulerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.scheduler.gen.FindResponse;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/**
 * Tests {@link TaskCommand}.
 */
@Guice(modules = {DcpBackendTestModule.class, TestModule.class, CommandTestModule.class})
public class TaskCommandTest {
  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeDcpRestClient apiFeDcpRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeDcpRestClient;

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

  @Inject
  private BasicServiceHost basicServiceHost;

  @Inject
  private ApiFeDcpRestClient apiFeDcpRestClient;

  public TestTaskCommand testTaskCommand;

  RootSchedulerClient rootSchedulerClient;

  HousekeeperClient housekeeperClient;

  DeployerClient deployerClient;

  @Inject
  private EntityLockBackend entityLockBackend;

  @Inject
  StepBackend stepBackend;

  private StepCommandFactory stepCommandFactory;

  @Inject
  private TaskBackend taskBackend;

  private HostClient hostClient;

  private TaskEntity task;

  private FindResponse findResponse;

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

  @AfterClass
  public static void afterClassCleanup() throws Throwable {
    commonHostAndClientTeardown();
  }

  @BeforeMethod
  public void setUp() throws Exception {
    rootSchedulerClient = mock(RootSchedulerClient.class);
    housekeeperClient = mock(HousekeeperClient.class);
    deployerClient = mock(DeployerClient.class);
    stepCommandFactory = mock(StepCommandFactory.class);
    hostClient = mock(HostClient.class);

    commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);

    String tenantId = DcpBackendTestHelper.createTenant(tenantDcpBackend, "t1");

    QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
    DcpBackendTestHelper.createTenantResourceTicket(resourceTicketDcpBackend,
        tenantId, "rt1", ImmutableList.of(ticketLimit));

    QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
    String projectId = DcpBackendTestHelper.createProject(projectDcpBackend,
        "p1", tenantId, "rt1", ImmutableList.of(projectLimit));

    DcpBackendTestHelper.createFlavors(flavorDcpBackend, flavorLoader.getAllFlavors());

    VmService.State vmState = new VmService.State();
    vmState.name = "vm-1";
    FlavorEntity flavorEntity = flavorDcpBackend.getEntityByNameAndKind("core-100", Vm.KIND);
    vmState.flavorId = flavorEntity.getId();
    vmState.imageId = UUID.randomUUID().toString();
    vmState.projectId = projectId;
    vmState.vmState = VmState.CREATING;
    dcpClient.post(VmServiceFactory.SELF_LINK, vmState);

    HostService.State hostState = new HostService.State();
    hostState.documentSelfLink = "agent-id";
    hostState.hostAddress = "host-ip";
    hostState.userName = "root";
    hostState.password = "password";
    hostState.usageTags = new HashSet<>();
    hostState.usageTags.add("VMFS");
    hostState.state = HostState.READY;
    dcpClient.post(HostServiceFactory.SELF_LINK, hostState);

    VmEntity vm = new VmEntity();
    task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);
    task.setSteps(new ArrayList<StepEntity>());
    testTaskCommand = new TestTaskCommand(dcpClient, rootSchedulerClient, hostClient, housekeeperClient,
        taskBackend, stepCommandFactory, task, deployerClient);

    findResponse = new FindResponse();
    Datastore datastore = new Datastore();
    datastore.setId("datastore-id");
    findResponse.setAgent_id("agent-id");
    findResponse.setDatastore(datastore);
    ServerAddress serverAddress = new ServerAddress();
    serverAddress.setHost("0.0.0.0");
    serverAddress.setPort(0);
    findResponse.setAddress(serverAddress);
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    commonHostDocumentsCleanup();
  }

  /**
   * Tests for entity lock management.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class, CommandTestModule.class})
  public static class TaskLockTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private EntityLockBackend entityLockBackend;

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    private TaskCommand command1;
    private TaskCommand command2;

    @BeforeMethod
    public void setUp() throws Exception {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);

      TaskBackend taskBackend = mock(TaskBackend.class);
      TaskEntity task1 = new TaskEntity();
      task1.setId(UUID.randomUUID().toString());
      String lockId = UUID.randomUUID().toString();
      task1.getToBeLockedEntityIds().add(lockId);

      TaskEntity task2 = new TaskEntity();
      task2.setId(UUID.randomUUID().toString());
      task2.getToBeLockedEntityIds().add(lockId);

      command1 = spy(new TaskCommand(
          mock(ApiFeDcpRestClient.class),
          mock(RootSchedulerClient.class),
          mock(HostClient.class),
          mock(HousekeeperClient.class),
          mock(DeployerClient.class),
          entityLockBackend,
          task1));
      command1.setTaskBackend(taskBackend);

      command2 = spy(new TaskCommand(
          mock(ApiFeDcpRestClient.class),
          mock(RootSchedulerClient.class),
          mock(HostClient.class),
          mock(HousekeeperClient.class),
          mock(DeployerClient.class),
          entityLockBackend,
          task2));
      command2.setTaskBackend(taskBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @Test
    public void testThrowConcurrentExceptionWhenLockAlreadyExists() throws Exception {
      // start a command but not complete it, hence keeping a lock active
      command1.markAsStarted();
      command2.run();

      // verify that execution of command2 resulted in a ConcurrentTaskException
      ArgumentCaptor<Throwable> exceptionArgument = ArgumentCaptor.forClass(Throwable.class);
      verify(command2, times(1)).markAsFailed(exceptionArgument.capture());
      assertTrue("Exception thrown should have been of type ConcurrentTaskException",
          exceptionArgument.getValue() instanceof ConcurrentTaskException);
    }

    @Test
    public void testLockAcquisitionByTaskWhenItAlreadyOwnsTheExistingLock() throws Exception {
      // start a command but not complete it, hence keeping a lock active
      command1.markAsStarted();
      // execution of the same command again should go through despite a lock already existing for the same task
      command1.run();
    }

    @Test
    public void testTaskOnlyCleansUpLocksThatItOwns() throws Exception {
      //start a command but not complete it, hence keeping a lock active
      command1.markAsStarted();
      command2.run();

      // verify that execution of command2 resulted in a ConcurrentTaskException
      ArgumentCaptor<Throwable> exceptionArgument = ArgumentCaptor.forClass(Throwable.class);
      verify(command2, times(1)).markAsFailed(exceptionArgument.capture());
      assertTrue("Exception thrown should have been of type ConcurrentTaskException",
          exceptionArgument.getValue() instanceof ConcurrentTaskException);

      command2.run();
      // verify that execution of command2 resulted in a ConcurrentTaskException
      // because the previous failed attempt did not result in releasing of lock held be command1
      verify(command2, times(2)).markAsFailed(exceptionArgument.capture());
      assertTrue("Exception thrown should have been of type ConcurrentTaskException",
          exceptionArgument.getValue() instanceof ConcurrentTaskException);
    }

    @Test
    public void testCleansUpOfLocksWhenTaskFails() throws Exception {
      //command1 one should fail in execution and then clean up locks before finishing
      doThrow(new ApiFeException()).when(command1).execute();
      command1.run();

      //command2 should be able to acquire locks and run if command1 had successfully released the locks on its failure
      command2.run();
    }
  }

  @Test
  public void testMarkAsStarted() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    command.markAsStarted();

    TaskEntity found = taskBackend.findById(task.getId());
    assertThat(found, is(notNullValue()));
    assertThat(found.getState(), is(TaskEntity.State.STARTED));
    assertThat(found.getEndTime(), is(nullValue()));
  }

  @DataProvider(name = "steps")
  public Object[][] getSteps() {
    return new Object[][]{
        {
            new StepEntity[]{
                createStep("step-1", StepEntity.State.QUEUED),
                createStep("step-2", StepEntity.State.QUEUED)
            },
            TaskEntity.State.QUEUED
        },
        {
            new StepEntity[]{
                createStep("step-1", StepEntity.State.COMPLETED),
                createDisableStep("step-2", StepEntity.State.QUEUED)
            },
            TaskEntity.State.QUEUED
        },
        {
            new StepEntity[]{
                createStep("step-1", StepEntity.State.COMPLETED),
                createStep("step-2", StepEntity.State.COMPLETED)
            },
            TaskEntity.State.COMPLETED
        }
    };
  }

  @Test(dataProvider = "steps")
  public void testMarkAsDone(StepEntity[] steps, TaskEntity.State state) throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);
    for (StepEntity step : steps) {
      task.addStep(step);
      doReturn(new TestStepCommand(command, stepBackend, step))
          .when(stepCommandFactory).createCommand(command, step);
    }

    command.markAsDone();

    TaskEntity found = taskBackend.findById(task.getId());
    assertThat(found, notNullValue());
    assertThat(found.getState(), is(state));
  }

  @Test
  public void testMarkAsFailed() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    command.markAsFailed(new ApiFeException("Something happened"));

    TaskEntity found = taskBackend.findById(task.getId());
    assertThat(found, is(notNullValue()));
    assertThat(found.getState(), is(TaskEntity.State.ERROR));
    assertThat(found.getEndTime(), is(notNullValue()));
  }

  @Test
  public void testExecute() throws Throwable {
    StepEntity[] steps = new StepEntity[]{
        createStep("step-1", StepEntity.State.COMPLETED),
        createDisableStep("step-2", StepEntity.State.QUEUED),
        createStep("step-3", StepEntity.State.QUEUED)
    };
    TestStepCommand[] stepCommands = new TestStepCommand[steps.length];
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    for (int i = 0; i < steps.length; i++) {
      StepEntity step = steps[i];
      task.addStep(step);
      stepCommands[i] = new TestStepCommand(command, stepBackend, step);
      doReturn(stepCommands[i]).when(stepCommandFactory).createCommand(command, step);
    }

    command.execute();

    assertThat(command.performed, is(true));
    assertThat(stepCommands[0].performed, is(false));
    assertThat(stepCommands[1].performed, is(false));
    assertThat(stepCommands[2].performed, is(true));
  }

  @Test
  public void testCleanup() {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    command.cleanup();
    verify(hostClient).close();
  }

  @Test
  public void testFindVmHostWithNoAgentId() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(rootSchedulerClient.findVm("vm-1")).thenReturn(findResponse);
    VmEntity vm = new VmEntity();
    vm.setId("vm-1");
    command.getHostClient(vm);

    assertThat(vm.getAgent(), is("agent-id"));
    assertThat(vm.getDatastore(), is("datastore-id"));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(rootSchedulerClient).findVm("vm-1");
    inOrder.verify(hostClient).setIpAndPort("0.0.0.0", 0);
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test
  public void testFindVmHostWithVmNotFoundException() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(rootSchedulerClient.findVm("vm-1"))
        .thenThrow(new com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException("Error"));
    VmEntity vm = new VmEntity();
    vm.setId("vm-1");

    try {
      command.getHostClient(vm);
      fail();
    } catch (VmNotFoundException ex) {
    }

    assertThat(vm.getAgent(), is(nullValue()));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(rootSchedulerClient).findVm("vm-1");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test
  public void testFindVmHostWithAgentId() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(hostClient.findVm("vm-1")).thenReturn(true);
    VmEntity vm = new VmEntity();
    vm.setId("vm-1");
    vm.setAgent("agent-id");
    command.getHostClient(vm);

    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(hostClient).setHostIp("host-ip");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test
  public void testVmGetHostClientHostWithNoAgentIdOrHost() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);
    when(rootSchedulerClient.findVm(anyString())).thenReturn(findResponse);

    VmEntity vm = new VmEntity();
    vm.setId("vm-1");
    command.getHostClient(vm);

    assertThat(vm.getAgent(), is("agent-id"));
    assertThat(vm.getDatastore(), is("datastore-id"));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(rootSchedulerClient).findVm(vm.getId());
    inOrder.verify(hostClient).setIpAndPort("0.0.0.0", 0);
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test
  public void testVmGetHostclientWithVmNotFoundException() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(rootSchedulerClient.findVm("vm-1"))
        .thenThrow(new com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException("Error"));
    VmEntity vm = new VmEntity();
    vm.setId("vm-1");

    try {
      command.getHostClient(vm);
      fail();
    } catch (VmNotFoundException ex) {
    }

    assertThat(vm.getAgent(), is(nullValue()));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(rootSchedulerClient).findVm("vm-1");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test
  public void testVmGetHostclientWithAgentId() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    VmEntity vm = new VmEntity();
    vm.setId("vm-1");
    vm.setAgent("agent-id");
    command.getHostClient(vm);

    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(hostClient).setHostIp("host-ip");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test
  public void testVmGetHostclientWithHostIp() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    VmEntity vm = new VmEntity();
    vm.setId("vm-1");
    vm.setHost("1.1.1.1");
    command.getHostClient(vm);

    assertThat(vm.getAgent(), is(nullValue()));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(hostClient).setHostIp("1.1.1.1");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test
  public void testVmGetHostClientWithAgentIdAndHostIp() throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    VmEntity vm = new VmEntity();
    vm.setId("vm-1");
    vm.setAgent("agentId");
    vm.setHost("1.1.1.1");
    command.getHostClient(vm);

    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(hostClient).setHostIp("1.1.1.1");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @DataProvider(name = "getDiskEntitiesParam")
  public Object[][] getDiskEntities() {
    return new Object[][]{
        {new PersistentDiskEntity()}
    };
  }

  @Test(dataProvider = "getDiskEntitiesParam")
  public void testFindDiskHostWithNoAgentId(BaseDiskEntity disk) throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(rootSchedulerClient.findDisk("disk-1")).thenReturn(findResponse);
    disk.setId("disk-1");
    command.findHost(disk);

    assertThat(disk.getAgent(), is("agent-id"));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(rootSchedulerClient).findDisk("disk-1");
    inOrder.verify(hostClient).setIpAndPort("0.0.0.0", 0);
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test(dataProvider = "getDiskEntitiesParam")
  public void testFindDiskHostWithDiskNotFoundException(BaseDiskEntity disk) throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(rootSchedulerClient.findDisk("disk-1"))
        .thenThrow(new com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException("Error"));
    disk.setId("disk-1");

    try {
      command.findHost(disk);
      fail();
    } catch (DiskNotFoundException ex) {
    }

    assertThat(disk.getAgent(), is(nullValue()));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(rootSchedulerClient).findDisk("disk-1");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test(dataProvider = "getDiskEntitiesParam")
  public void testFindDiskHostWithStaleAgentId(BaseDiskEntity disk) throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(hostClient.findDisk("disk-1")).thenReturn(false);
    when(rootSchedulerClient.findDisk("disk-1")).thenReturn(findResponse);
    disk.setId("disk-1");
    disk.setAgent("agent-id");
    command.findHost(disk);

    assertThat(disk.getAgent(), is("agent-id"));
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).findDisk("disk-1");
    inOrder.verify(rootSchedulerClient).findDisk("disk-1");
    inOrder.verify(hostClient).setIpAndPort("0.0.0.0", 0);
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  @Test(dataProvider = "getDiskEntitiesParam")
  public void testFindDiskHostWithAgentId(BaseDiskEntity disk) throws Exception {
    TestTaskCommand command = new TestTaskCommand(apiFeDcpRestClient, rootSchedulerClient, hostClient,
        housekeeperClient, taskBackend, stepCommandFactory, task, deployerClient);

    when(hostClient.findDisk("disk-1")).thenReturn(true);
    disk.setId("disk-1");
    disk.setAgent("agent-id");
    command.findHost(disk);
    InOrder inOrder = inOrder(rootSchedulerClient, hostClient);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).findDisk("disk-1");
    verifyNoMoreInteractions(rootSchedulerClient, hostClient);
  }

  private StepEntity createDisableStep(String id, StepEntity.State stepState) {
    StepEntity step = createStep(id, stepState);
    step.setDisabled(true);
    return step;
  }

  private StepEntity createStep(String id, StepEntity.State stepState) {
    StepEntity step = new StepEntity();
    step.setId(id);
    step.setState(stepState);

    return step;
  }

  /**
   * A very basic implementation of TaskCommand.
   */
  public class TestTaskCommand extends TaskCommand {

    public boolean performed = false;
    public boolean cleanedUp = false;

    @Inject
    public TestTaskCommand(ApiFeDcpRestClient apiFeDcpRestClient, RootSchedulerClient rootSchedulerClient,
                           HostClient hostClient, HousekeeperClient housekeeperClient, TaskBackend taskBackend,
                           StepCommandFactory stepCommandFactory, TaskEntity task, DeployerClient deployerClient) {
      super(apiFeDcpRestClient, rootSchedulerClient, hostClient, housekeeperClient,
          deployerClient, entityLockBackend, task);
      setReservation("reservation-id");
      setTaskBackend(taskBackend);
      setStepCommandFactory(stepCommandFactory);
    }

    @Override
    protected void execute() throws ApiFeException, InterruptedException, RpcException {
      super.execute();
      performed = true;
    }

    @Override
    protected void cleanup() {
      super.cleanup();
      cleanedUp = true;
    }
  }

  private class TestStepCommand extends StepCommand {

    public boolean performed = false;

    private TestStepCommand(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
      super(taskCommand, stepBackend, step);
    }

    @Override
    protected void markAsStarted() {
      step.setState(StepEntity.State.STARTED);
    }

    @Override
    protected void markAsDone() {
      step.setState(StepEntity.State.COMPLETED);
    }

    @Override
    protected void markAsFailed(Throwable t) {
      step.setState(StepEntity.State.ERROR);
    }

    @Override
    public void execute() {
      performed = true;
    }

    @Override
    protected void cleanup() {
    }
  }
}
