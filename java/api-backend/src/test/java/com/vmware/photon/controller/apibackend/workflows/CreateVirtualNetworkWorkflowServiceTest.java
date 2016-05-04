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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHelper;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.EnumSet;

/**
 * This class implements tests for the {@link CreateVirtualNetworkWorkflowService} class.
 */
public class CreateVirtualNetworkWorkflowServiceTest {

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This method creates a new State object to create a new CreateVirtualNetworkTaskService instance.
   */
  private CreateVirtualNetworkWorkflowDocument buildValidStartState(
      TaskState.TaskStage stage,
      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage,
      int controlFlags) {
    CreateVirtualNetworkWorkflowDocument startState = new CreateVirtualNetworkWorkflowDocument();
    startState.taskState = new CreateVirtualNetworkWorkflowDocument.TaskState();
    startState.taskState.stage = stage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.name = "name";
    startState.description = "desc";

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * CreateVirtualNetworkTaskService instance.
   */
  private CreateVirtualNetworkWorkflowDocument buildPatch(
      TaskState.TaskStage stage,
      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage) {
    CreateVirtualNetworkWorkflowDocument state = new CreateVirtualNetworkWorkflowDocument();
    state.taskState = new CreateVirtualNetworkWorkflowDocument.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    return state;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    private CreateVirtualNetworkWorkflowService createVirtualNetworkWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      createVirtualNetworkWorkflowService = new CreateVirtualNetworkWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      createVirtualNetworkWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(createVirtualNetworkWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * Tests that when {@link CreateVirtualNetworkWorkflowService#handleCreate} is called, the workflow will
   * validate the state object and behave correctly.
   */
  public class HandleCreateTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleCreate()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Verifies that when a field of the initial state has null value and is annotated with
     * default value, the workflow will initialize the state with the default value and succeed.
     */
    @Test
    public void succeedsWithNullDefaultFields() throws Throwable {

      startState.taskState = null;
      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when a field of the initial state has null value but is annotated as mandatory,
     * the workflow will validate the state and fail.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class)
    public void failsWithNullMandatoryFields() throws Throwable {

      startState.name = null;
      testEnvironment.callServiceAndWaitForState(
          CreateVirtualNetworkWorkflowService.FACTORY_LINK,
          startState,
          CreateVirtualNetworkWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    /**
     * Verifies that the workflow will create a {@link VirtualNetworkService.State} entity in cloud-store.
     */
    @Test
    public void succeedsToCreateVirtualNetworkServiceState() throws Throwable {

      startState.controlFlags = new ControlFlags.Builder()
          .disableOperationProcessingOnHandleStart()
          .disableOperationProcessingOnHandlePatch()
          .build();
      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.virtualNetworkServiceState, notNullValue());
      VirtualNetworkService.State expectedVirtualNetworkServiceState = finalState.virtualNetworkServiceState;
      VirtualNetworkService.State actualVirtualNetworkServiceState = testEnvironment.getServiceState(
          finalState.virtualNetworkServiceState.documentSelfLink,
          VirtualNetworkService.State.class);

      assertThat(actualVirtualNetworkServiceState, notNullValue());
      assertEquals(actualVirtualNetworkServiceState.name, expectedVirtualNetworkServiceState.name);
      assertEquals(actualVirtualNetworkServiceState.description, expectedVirtualNetworkServiceState.description);
      assertEquals(actualVirtualNetworkServiceState.state, expectedVirtualNetworkServiceState.state);
      assertEquals(actualVirtualNetworkServiceState.routingType, expectedVirtualNetworkServiceState.routingType);
    }
  }

  /**
   * Test that when {@link CreateVirtualNetworkWorkflowService#handleStart} is called, the workflow will
   * validate the state object and behave correctly.
   */
  public class HandleStartTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Verifies that when a valid start state is given, the workflow will validate the state and succeed.
     */
    @Test
    public void succeedsWithValidStartState() throws Throwable {

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when an invalid start state is given, the workflow will validate the state and fail.
     */
    @Test(dataProvider = "InvalidStartState", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartState(TaskState.TaskStage stage,
                                           CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {

      startState = buildValidStartState(stage, subStage, new ControlFlags.Builder()
          .disableOperationProcessingOnHandleStart()
          .disableOperationProcessingOnHandlePatch()
          .build());

      testEnvironment.callServiceAndWaitForState(
          CreateVirtualNetworkWorkflowService.FACTORY_LINK,
          startState,
          CreateVirtualNetworkWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "InvalidStartState")
    public Object[][] getInvalidStartStateTestData() {
      return new Object[][]{
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED, null},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED, null},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED, null},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED, null},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER}
      };
    }
  }

  /**
   * Tests that when {@link CreateVirtualNetworkWorkflowService#handlePatch} is called, the workflow will
   * validate the state object and behave correctly.
   */
  public class HandlePatchTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandlePatch()
              .disableOperationProcessingOnStageTransition()
              .build());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Verifies that when a valid stage/sub-stage patch state is given, the workflow will validate
     * the state and succeed.
     */
    @Test(dataProvider = "ValidStageAndSubStagePatch")
    public void succeedsWithValidStageAndSubStagePatch(
        TaskState.TaskStage patchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage patchSubStage
        ) throws Throwable {

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                    == state.taskState.subStage);

      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(patchStage, patchSubStage);
      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(CreateVirtualNetworkWorkflowDocument.class);

      assertThat(finalState.taskState.stage, is(patchStage));
      assertThat(finalState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageAndSubStagePatch")
    public Object[][] getValidStageAndSubStagePatch() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER},

          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * Verifies that when an invalid stage/sub-stage patch state is given, the workflow will validate
     * the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "InvalidStageAndSubStagePatch")
    public void failsWithInvalidStageAndSubStagePatch(
        TaskState.TaskStage firstPatchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage firstPatchSubStage,
        TaskState.TaskStage secondPatchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage secondPatchSubStage)
        throws Throwable {

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(firstPatchStage, firstPatchSubStage);
      if (firstPatchStage != TaskState.TaskStage.STARTED ||
          firstPatchSubStage != CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION) {
        finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
            .getBody(CreateVirtualNetworkWorkflowDocument.class);
      }

      patchState = buildPatch(secondPatchStage, secondPatchSubStage);
      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(CreateVirtualNetworkWorkflowDocument.class);
    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION,
           TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},

          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},

          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},

          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},

          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},

          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},

      };
    }

    /**
     * Verifies that when a immutable field is set to non-null value in the patch state, the workflow will
     * validate the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "ImmutableFields")
    public void failsWithNonNullImmutableFieldPatch(String fieldName) throws Throwable {

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.STARTED,
          CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(CreateVirtualNetworkWorkflowDocument.class);
    }

    @DataProvider(name = "ImmutableFields")
    public Object[][] getImmutableFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateVirtualNetworkWorkflowDocument.class, Immutable.class));
    }
  }

  /**
   * Tests end-to-end scenarios of the {@link CreateVirtualNetworkWorkflowService}.
   */
  public class EndToEndTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private DeploymentService.State deploymentStartState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .build());

      deploymentStartState = ReflectionUtils.buildValidStartState(DeploymentService.State.class);
      deploymentStartState.virtualNetworkEnabled = true;
      deploymentStartState.networkManagerAddress = "networkManagerAddress";
      deploymentStartState.networkManagerUsername = "networkManagerUsername";
      deploymentStartState.networkManagerPassword = "networkManagerPassword";
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    /**
     * Verifies that the workflow succeeds to create one and only one virtual network entity in cloud-store.
     */
    @Test(dataProvider = "hostCount")
    public void succeedsToCreateVirtualNetwork(int hostCount) throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      testEnvironment.callServiceAndWaitForState(
          DeploymentServiceFactory.SELF_LINK,
          deploymentStartState,
          DeploymentService.State.class,
          (state) -> true);

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that one and only one virtual network entity is created in cloud-store.
      assertThat(finalState.virtualNetworkServiceState, notNullValue());
      VirtualNetworkService.State expectedVirtualNetworkServiceState = finalState.virtualNetworkServiceState;
      VirtualNetworkService.State actualVirtualNetworkServiceState = testEnvironment.getServiceState(
          finalState.virtualNetworkServiceState.documentSelfLink,
          VirtualNetworkService.State.class);

      assertThat(actualVirtualNetworkServiceState, notNullValue());
      assertEquals(actualVirtualNetworkServiceState.name, expectedVirtualNetworkServiceState.name);
      assertEquals(actualVirtualNetworkServiceState.description, expectedVirtualNetworkServiceState.description);
      assertEquals(actualVirtualNetworkServiceState.state, expectedVirtualNetworkServiceState.state);
      assertEquals(actualVirtualNetworkServiceState.routingType, expectedVirtualNetworkServiceState.routingType);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(VirtualNetworkService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(1));

      // Verifies that NSX configuration is cached in the service document.
      assertThat(finalState.nsxManagerEndpoint, is("networkManagerAddress"));
      assertThat(finalState.username, is("networkManagerUsername"));
      assertThat(finalState.password, is("networkManagerPassword"));
    }

    @DataProvider(name = "hostCount")
    public Object[][] getHostCount() {
      return new Object[][]{
          {1},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }
  }
}
