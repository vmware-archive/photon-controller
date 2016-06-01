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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidator;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.service.exceptions.ManagementVmAddressAlreadyInUseException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Executors;

/**
 * This class implements tests for {@link ValidateHostTaskService} class.
 */
public class ValidateHostTaskServiceTest {

  private TestHost host;
  private ValidateHostTaskService service;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ValidateHostTaskService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new ValidateHostTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    /**
     * This test verifies that service instances can be created with specific
     * start states.
     *
     * @param stage Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage stage)
        throws Throwable {

      ValidateHostTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ValidateHostTaskService.State savedState = host.getServiceState(ValidateHostTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.hostAddress, notNullValue());
      assertThat(savedState.userName, notNullValue());
      assertThat(savedState.password, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that a service instance which is started in the CREATED state
     * is transitioned to STARTED state as part of start operation handling.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testMinimalStartStateChanged() throws Throwable {

      ValidateHostTaskService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ValidateHostTaskService.State savedState = host.getServiceState(ValidateHostTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    /**
     * This test verifies that a service instance which is started in the final states
     * is not transitioned to another state as part of start operation handling.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      ValidateHostTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ValidateHostTaskService.State savedState = host.getServiceState(ValidateHostTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "startStateNotChanged")
    public Object[][] getStartStateNotChanged() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      ValidateHostTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return new Object[][]{
          {"hostAddress"},
          {"userName"},
          {"password"},
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      service = new ValidateHostTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    /**
     * This test verifies that legal stage transitions succeed.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      ValidateHostTaskService.State startState = buildValidStartupState(startStage);

      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ValidateHostTaskService.State patchState = buildValidPatchState(targetStage);

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ValidateHostTaskService.State savedState = host.getServiceState(ValidateHostTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that illegal stage transitions fail.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      ValidateHostTaskService.State startState = buildValidStartupState(startStage);

      host.startServiceSynchronously(service, startState);

      ValidateHostTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to invalid start state");
      } catch (XenonRuntimeException e) {
      }
    }

    @DataProvider(name = "illegalStageUpdatesInvalidPatch")
    public Object[][] getIllegalStageUpdatesInvalidPatch() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      ValidateHostTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      ValidateHostTaskService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, declaredField.getType().newInstance());

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return new Object[][]{
          {"hostAddress"},
          {"userName"},
          {"password"}
      };
    }
  }

  /**
   * End-to-end tests for the create VM task.
   */
  public class EndToEndTest {

    private ValidateHostTaskService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory;
    private HostManagementVmAddressValidator hostManagementVmAddressValidator;

    @BeforeMethod
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      startState = buildValidStartupState();
      startState.controlFlags = null;
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      hostManagementVmAddressValidatorFactory = mock(HostManagementVmAddressValidatorFactory.class);
      hostManagementVmAddressValidator = mock(HostManagementVmAddressValidator.class);

      doReturn(hostManagementVmAddressValidator).when(hostManagementVmAddressValidatorFactory)
          .create(any(String.class));
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .hostManagementVmAddressValidatorBuilderFactory(hostManagementVmAddressValidatorFactory)
          .hostCount(1)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    /**
     * This test verifies an successful end-to-end scenario.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndSuccess() throws Throwable {
      cloudStoreMachine.sendPostAndWait(HostServiceFactory.SELF_LINK, buildValidHost());
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      doReturn(true).when(hostManagementVmAddressValidator).call();

      ValidateHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ValidateHostTaskFactoryService.SELF_LINK,
              startState,
              ValidateHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndFailureValidateHostCredentialsThrowsInvalidLoginException() throws Throwable {
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, false);

      ValidateHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ValidateHostTaskFactoryService.SELF_LINK,
              startState,
              ValidateHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testEndToEndFailureValidateHostMgmtVmAddressThrowsAddressInUseException() throws Throwable {
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      doThrow(new ManagementVmAddressAlreadyInUseException("Address in use"))
          .when(hostManagementVmAddressValidator)
          .call();

      ValidateHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ValidateHostTaskFactoryService.SELF_LINK,
              startState,
              ValidateHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Address in use"));
      assertThat(finalState.taskState.resultCode,
          is(ValidateHostTaskService.TaskState.ResultCode.ManagementVmAddressAlreadyInUse));
    }

    @Test
    public void testEndToEndFailureValdidateUniqueMgmtVmAddress() throws Throwable {
      // we are simulating the API-FE behavior which first posts the document to cloud store and then
      // invokes the verification task
      cloudStoreMachine.sendPostAndWait(HostServiceFactory.SELF_LINK, buildValidHost());
      cloudStoreMachine.sendPostAndWait(HostServiceFactory.SELF_LINK, buildValidHost());

      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      doReturn(true).when(hostManagementVmAddressValidator).call();

      ValidateHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ValidateHostTaskFactoryService.SELF_LINK,
              startState,
              ValidateHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      String expectedMessage
        = "The managementIp [managementNetworkIp] is already assign to host [differentHostAddress].";
      assertThat(finalState.taskState.failure.message, is(expectedMessage));
    }
  }

  private ValidateHostTaskService.State buildValidStartupState() {
    return buildValidStartupState(
        TaskState.TaskStage.CREATED);
  }

  private ValidateHostTaskService.State buildValidStartupState(
      TaskState.TaskStage stage) {
    ValidateHostTaskService.State state = new ValidateHostTaskService.State();
    state.taskState = new ValidateHostTaskService.TaskState();
    state.taskState.stage = stage;
    state.hostAddress = "hostAddress";
    state.userName = "userName";
    state.password = "password";
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name()));
    state.metadata = new HashMap<>();
    state.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE, "managementDatastore");
    state.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER, "managementNetworkDnsServer");
    state.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY, "managementNetworkGateway");
    state.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP, "managementPortGroup");
    state.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "managementNetworkIp");
    state.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK, "managementNetworkNetmask");
    state.metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES, "datastore1, datastore2");
    state.metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS, "VM Network, Management VLan");
    state.networks = new HashSet<>();
    state.networks.add("network1");
    state.networks.add("network2");
    state.dataStores = new HashSet<>();
    state.dataStores.add("datastore1");
    state.dataStores.add("datastore2");
    return state;
  }

  private HostService.State buildValidHost() {
    HostService.State host = new HostService.State();
    host.hostAddress = "differentHostAddress";
    host.userName = "userName";
    host.password = "password";
    host.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name()));
    host.metadata = new HashMap<>();
    host.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE, "managementDatastore");
    host.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER, "managementNetworkDnsServer");
    host.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY, "managementNetworkGateway");
    host.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP, "managementPortGroup");
    host.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "managementNetworkIp");
    host.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK, "managementNetworkNetmask");
    host.metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES, "datastore1, datastore2");
    host.metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS, "VM Network, Management VLan");
    host.state = HostState.CREATING;
    return host;
  }

  private ValidateHostTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private ValidateHostTaskService.State buildValidPatchState(TaskState.TaskStage stage) {
    ValidateHostTaskService.State state = new ValidateHostTaskService.State();
    state.taskState = new ValidateHostTaskService.TaskState();
    state.taskState.stage = stage;
    return state;
  }
}
