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

import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHelper;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.EnumSet;

/**
 * This class implements tests for the {@link com.vmware.photon.controller.apibackend.workflows
 * .DeleteVirtualNetworkWorkflowService} class.
 */
public class DeleteVirtualNetworkWorkflowServiceTest {

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This method creates a new DeleteVirtualNetworkWorkflowDocument object to create
   * a new DeleteVirtualNetworkTaskService instance.
   */
  private DeleteVirtualNetworkWorkflowDocument buildValidStartState(
      TaskState.TaskStage stage,
      DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage subStage,
      int controlFlags,
      String virtualNetworkId) {
    DeleteVirtualNetworkWorkflowDocument startState = new DeleteVirtualNetworkWorkflowDocument();
    startState.taskState = new DeleteVirtualNetworkWorkflowDocument.TaskState();
    startState.taskState.stage = stage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.virtualNetworkId = virtualNetworkId;

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * DeleteVirtualNetworkTaskService instance.
   */
  private DeleteVirtualNetworkWorkflowDocument buildPatch(
      TaskState.TaskStage stage,
      DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage subStage) {
    DeleteVirtualNetworkWorkflowDocument state = new DeleteVirtualNetworkWorkflowDocument();
    state.taskState = new DeleteVirtualNetworkWorkflowDocument.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    return state;
  }

  /**
   * This method creates VirtualNetworkService.State object in cloud-store.
   */
  private VirtualNetworkService.State createVirtualNetworkDocumentInCloudStore(TestEnvironment testEnvironment)
      throws Throwable {
    VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
    virtualNetwork.name = "virtual_network_name";
    virtualNetwork.state = NetworkState.CREATING;
    virtualNetwork.routingType = RoutingType.ROUTED;
    virtualNetwork.parentId = "parentId";
    virtualNetwork.parentKind = "parentKind";
    virtualNetwork.tier0RouterId = "logical_tier0_router_id";
    virtualNetwork.logicalRouterId = "logical_tier1_router_id";
    virtualNetwork.logicalSwitchId = "logical_switch_id";

    Operation result = testEnvironment.sendPostAndWait(VirtualNetworkService.FACTORY_LINK, virtualNetwork);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    VirtualNetworkService.State createdState = result.getBody(VirtualNetworkService.State.class);
    VirtualNetworkService.State patchState = new VirtualNetworkService.State();
    patchState.state = NetworkState.READY;
    result = testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(VirtualNetworkService.State.class);
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    private DeleteVirtualNetworkWorkflowService deleteVirtualNetworkWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      deleteVirtualNetworkWorkflowService = new DeleteVirtualNetworkWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      deleteVirtualNetworkWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(deleteVirtualNetworkWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * Tests that when {@link com.vmware.photon.controller.apibackend.workflows
   * .DeleteVirtualNetworkWorkflowService#handleCreate} is called, the workflow will validate the state object and
   * behave correctly.
   */
  public class HandleCreateTest {

    private DeleteVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));
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
      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when a field of the initial state has null value but is annotated as mandatory,
     * the workflow will validate the state and fail.
     * @throws Throwable
     */
    @Test
    public void failsWithNullMandatoryFields() throws Throwable {

      startState.virtualNetworkId = null;
      try {
        testEnvironment.callServiceAndWaitForState(
            DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
            startState,
            DeleteVirtualNetworkWorkflowDocument.class,
            (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), containsString("virtualNetworkId cannot be null"));
      }

    }
  }

  /**
   * Test that when {@link com.vmware.photon.controller.apibackend.workflows
   * .DeleteVirtualNetworkWorkflowService#handleStart} is called, the workflow will validate the state object and
   * behave correctly.
   */
  public class HandleStartTest {

    private DeleteVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;
    private VirtualNetworkService.State virtualNetworkDocument;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));
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

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when an invalid start state is given, the workflow will validate the state and fail.
     */
    @Test(dataProvider = "InvalidStartState", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartState(TaskState.TaskStage stage,
                                           DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {

      startState = buildValidStartState(stage, subStage, new ControlFlags.Builder()
          .disableOperationProcessingOnHandleStart()
          .disableOperationProcessingOnHandlePatch()
          .build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));

      testEnvironment.callServiceAndWaitForState(
          DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
          startState,
          DeleteVirtualNetworkWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "InvalidStartState")
    public Object[][] getInvalidStartStateTestData() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.CREATED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.CREATED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},
          {TaskState.TaskStage.CREATED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH},

          {TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.STARTED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.STARTED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.STARTED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},
          {TaskState.TaskStage.STARTED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH},

          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.FINISHED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.FINISHED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},
          {TaskState.TaskStage.FINISHED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH},

          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.FAILED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.FAILED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},
          {TaskState.TaskStage.FAILED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH},

          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.CANCELLED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.CANCELLED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.CANCELLED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},
          {TaskState.TaskStage.CANCELLED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH}
      };
    }
  }

  /**
   * Tests that when {@link com.vmware.photon.controller.apibackend.workflows
   * .DeleteVirtualNetworkWorkflowService#handlePatch} is called, the workflow will validate the state object and
   * behave correctly.
   */
  public class HandlePatchTest {

    private DeleteVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandlePatch()
              .disableOperationProcessingOnStageTransition()
              .build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));
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
        DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage patchSubStage
        ) throws Throwable {

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                    == state.taskState.subStage);

      DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(patchStage, patchSubStage);
      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(DeleteVirtualNetworkWorkflowDocument.class);

      assertThat(finalState.taskState.stage, is(patchStage));
      assertThat(finalState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageAndSubStagePatch")
    public Object[][] getValidStageAndSubStagePatch() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.STARTED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},
          {TaskState.TaskStage.STARTED,
              DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH},

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
        DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage firstPatchSubStage,
        TaskState.TaskStage secondPatchStage,
        DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage secondPatchSubStage)
        throws Throwable {

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(firstPatchStage, firstPatchSubStage);
      if (firstPatchStage != TaskState.TaskStage.STARTED ||
          firstPatchSubStage != DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION) {
        finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
            .getBody(DeleteVirtualNetworkWorkflowDocument.class);
      }

      patchState = buildPatch(secondPatchStage, secondPatchSubStage);
      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(DeleteVirtualNetworkWorkflowDocument.class);
    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION,
           TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},

          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},

          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},

          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.FINISHED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},

          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.CANCELLED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},

          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS},
          {TaskState.TaskStage.FAILED, null,
           TaskState.TaskStage.STARTED, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER},

      };
    }

    /**
     * Verifies that when a immutable field is set to non-null value in the patch state, the workflow will
     * validate the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "ImmutableFields")
    public void failsWithNonNullImmutableFieldPatch(String fieldName) throws Throwable {
      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.STARTED,
          DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(DeleteVirtualNetworkWorkflowDocument.class);

    }

    @DataProvider(name = "ImmutableFields")
    public Object[][] getImmutableFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeleteVirtualNetworkWorkflowDocument.class, Immutable.class));
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* cannot be set or changed in a patch",
        dataProvider = "writeOnceFields")
    public void failsWithChangeWriteOnceFields(String fieldName) throws Throwable {
      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.STARTED,
          DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, "value1");

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(DeleteVirtualNetworkWorkflowDocument.class);

      patchState = buildPatch(TaskState.TaskStage.STARTED,
          DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS);
      declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, "value2");

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(DeleteVirtualNetworkWorkflowDocument.class);
    }

    @DataProvider(name = "writeOnceFields")
    public Object[][] getWriteOnceFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeleteVirtualNetworkWorkflowDocument.class, WriteOnce.class));
    }
  }

  /**
   * Tests end-to-end scenarios of the {@link com.vmware.photon.controller.apibackend.workflows
   * .DeleteVirtualNetworkWorkflowService}.
   */
  public class EndToEndTest {

    private static final String NETWORK_MANAGER_ADDRESS = "networkManagerAddress";
    private static final String NETWORK_MANAGER_USERNAME = "networkManagerUsername";
    private static final String NETWORK_MANAGER_PASSWORD = "networkManagerPassword";
    private static final String NETWORK_ZONE_ID = "networkZoneId";
    private static final String NETWORK_TOP_ROUTER_ID = "networkTopRouterId";

    private DeleteVirtualNetworkWorkflowDocument startState;
    private DeploymentService.State deploymentStartState;
    private NsxClientFactory nsxClientFactory;
    private NsxClientMock nsxClientMock;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {

      deploymentStartState = ReflectionUtils.buildValidStartState(DeploymentService.State.class);
      deploymentStartState.virtualNetworkEnabled = true;
      deploymentStartState.networkManagerAddress = NETWORK_MANAGER_ADDRESS;
      deploymentStartState.networkManagerUsername = NETWORK_MANAGER_USERNAME;
      deploymentStartState.networkManagerPassword = NETWORK_MANAGER_PASSWORD;
      deploymentStartState.networkZoneId = NETWORK_ZONE_ID;
      deploymentStartState.networkTopRouterId = NETWORK_TOP_ROUTER_ID;

      nsxClientFactory = mock(NsxClientFactory.class);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    /**
     * Verifies that the workflow succeeds to delete a virtual network.
     */
    @Test(dataProvider = "hostCount")
    public void succeedsToDeleteVirtualNetwork(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true)
          .deleteLogicalPort(true)
          .deleteLogicalRouter(true)
          .deleteLogicalSwitch(true)
          .checkLogicalRouterPortExistence(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder().build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));

      testEnvironment.callServiceAndWaitForState(
          DeploymentServiceFactory.SELF_LINK,
          deploymentStartState,
          DeploymentService.State.class,
          (state) -> true);

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies the DeleteVirtualNetworkWorkflowDocument.
      assertThat(finalState.nsxManagerEndpoint, is(NETWORK_MANAGER_ADDRESS));
      assertThat(finalState.username, is(NETWORK_MANAGER_USERNAME));
      assertThat(finalState.password, is(NETWORK_MANAGER_PASSWORD));
      assertThat(finalState.taskServiceEntity, notNullValue());
      assertThat(finalState.taskServiceEntity.documentSelfLink, notNullValue());
      assertThat(finalState.taskServiceEntity.state, is(NetworkState.DELETED));
      assertThat(finalState.taskServiceState, notNullValue());
      assertThat(finalState.taskServiceState.state, is(TaskService.State.TaskState.COMPLETED));

      // Verifies that the virtual network entity is set to DELETED in cloud-store.
      VirtualNetworkService.State savedVirtualNetworkDocument = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          VirtualNetworkService.State.class);
      assertThat(savedVirtualNetworkDocument, notNullValue());
      assertThat(savedVirtualNetworkDocument.state, is(NetworkState.DELETED));
    }

    /**
     * Verifies when GET_NSX_CONFIGURATION sub-stage fails, the workflow will progress to FAILED state,
     * and no NSX configuration is cached in the service document. We simulate the failure by not creating
     * a deployment service entity in cloud-store.
     */
    @Test(dataProvider = "hostCount")
    public void failsToGetNsxConfiguration(int hostCount) throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder().build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that NSX configuration is empty in the service document.
      assertThat(finalState.nsxManagerEndpoint, nullValue());
      assertThat(finalState.username, nullValue());
      assertThat(finalState.password, nullValue());
    }

    /**
     * Verifies when DELETE_LOGICAL_PORTS sub-stage fails, the workflow will progress to FAILED state.
     */
    @Test(dataProvider = "hostCount")
    public void failsToDeleteLogicalPorts(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder().build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));

      testEnvironment.callServiceAndWaitForState(
          DeploymentServiceFactory.SELF_LINK,
          deploymentStartState,
          DeploymentService.State.class,
          (state) -> true);

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies the cached task entity document.
      assertThat(finalState.taskServiceState.state, is(TaskService.State.TaskState.ERROR));

      // Verifies that the task entity document is set to ERROR in cloud-store.
      TaskService.State taskServiceState = testEnvironment.getServiceState(finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);
      assertThat(taskServiceState, notNullValue());
      assertThat(taskServiceState.state, is(TaskService.State.TaskState.ERROR));
    }

    /**
     * Verifies that when DELETE_LOGICAL_ROUTER sub-stage fails, the workflow will progress to FAILED state.
     */
    @Test(dataProvider = "hostCount")
    public void failsToDeleteLogicalRouter(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true)
          .deleteLogicalPort(true)
          .checkLogicalRouterPortExistence(true)
          .deleteLogicalRouter(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder().build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));

      testEnvironment.callServiceAndWaitForState(
          DeploymentServiceFactory.SELF_LINK,
          deploymentStartState,
          DeploymentService.State.class,
          (state) -> true);

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies the cached task entity document.
      assertThat(finalState.taskServiceState.state, is(TaskService.State.TaskState.ERROR));

      // Verifies that the task entity document is set to ERROR in cloud-store.
      TaskService.State taskServiceState = testEnvironment.getServiceState(finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);
      assertThat(taskServiceState, notNullValue());
      assertThat(taskServiceState.state, is(TaskService.State.TaskState.ERROR));
    }

    /**
     * Verifies that when DELETE_LOGICAL_SWITCH sub-stage fails, the workflow will progress to FAILED state.
     */
    @Test(dataProvider = "hostCount")
    public void failsToDeleteLogicalSwitch(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true)
          .deleteLogicalPort(true)
          .deleteLogicalRouter(true)
          .deleteLogicalSwitch(false)
          .checkLogicalRouterPortExistence(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkDocumentInCloudStore(testEnvironment);
      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder().build(),
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink));

      testEnvironment.callServiceAndWaitForState(
          DeploymentServiceFactory.SELF_LINK,
          deploymentStartState,
          DeploymentService.State.class,
          (state) -> true);

      DeleteVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              DeleteVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies the cached task entity document.
      assertThat(finalState.taskServiceState.state, is(TaskService.State.TaskState.ERROR));

      // Verifies that the task entity document is set to ERROR in cloud-store.
      TaskService.State taskServiceState = testEnvironment.getServiceState(finalState.taskServiceState.documentSelfLink,
          TaskService.State.class);
      assertThat(taskServiceState, notNullValue());
      assertThat(taskServiceState.state, is(TaskService.State.TaskState.ERROR));
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
