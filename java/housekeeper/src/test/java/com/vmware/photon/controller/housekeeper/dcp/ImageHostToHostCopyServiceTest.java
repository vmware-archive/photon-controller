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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.ServiceStats;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.ImageNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.host.gen.CopyImageResultCode;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientCopyImageErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetHostsForDatastoreErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorSuccessMock;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Tests {@link com.vmware.photon.controller.housekeeper.dcp.ImageHostToHostCopyService}.
 */
public class ImageHostToHostCopyServiceTest {

  private static final Logger logger = LoggerFactory.getLogger(ImageHostToHostCopyServiceTest.class);
  private TestHost host;
  private ImageHostToHostCopyService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ImageHostToHostCopyService.State buildValidStartupState() {
    ImageHostToHostCopyService.State state = new ImageHostToHostCopyService.State();
    state.isSelfProgressionDisabled = true;
    state.image = "image1";
    state.sourceDataStore = "source-datastore";
    state.destinationDataStore = "datastore1-inv";

    return state;
  }

  private ImageHostToHostCopyService.State buildValidStartupState(TaskState.TaskStage stage) {
    ImageHostToHostCopyService.State state = buildValidStartupState();
    state.taskInfo = new TaskState();
    state.taskInfo.stage = stage;

    return state;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageHostToHostCopyService());
    }

    /**
     * Test that the service starts with the expected capabilities.
     */
    @Test
    public void testServiceOptions() {
      // Factory capability is implicitly added as part of the factory constructor.
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Test getZookeeperHostMonitor.
   */
  public class GetZookeeperHostMonitorTest {
    @BeforeMethod
    public void setUp() {
      service = spy(new ImageHostToHostCopyService());
    }

    @Test(expectedExceptions = ClassCastException.class,
        expectedExceptionsMessageRegExp = "^.*ServiceHost.*cannot be cast to.*ZookeeperHostMonitorProvider$")
    public void testClassCastError() {
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      service.getZookeeperHostMonitor();
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageHostToHostCopyService());
      host = TestHost.create(mock(HostClient.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * Test start of service with valid initial state.
     *
     * @throws Throwable
     */
    @Test
    public void testEmptyStartState() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @Test
    public void testStartStateWithEmptyTaskInfo() throws Throwable {
      ImageHostToHostCopyService.State startState = buildValidStartupState();
      startState.taskInfo = new TaskState();
      host.startServiceSynchronously(service, startState);

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
    }

    @Test
    public void testStartStateWithCREATEDStage() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(TaskState.TaskStage.CREATED));

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
    }

    @DataProvider(name = "targetStages")
    public Object[][] getTargetStages() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * Test that start stage is not changed on service start up. This is expected behaviour when
     * initial state is STARTED, FINISHED or FAILED.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "targetStages")
    public void testStartStageIsNotChanged(TaskState.TaskStage targetStage) throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(targetStage));

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(targetStage));
    }

    @Test
    public void testInvalidStartStateWithoutImage() throws Throwable {
      try {
        ImageCopyService.State startState = new ImageCopyService.State();
        startState.isSelfProgressionDisabled = true;
        host.startServiceSynchronously(service, startState);
      } catch (NullPointerException ex) {
        assertThat(ex.getMessage(), is("image not provided"));
      }
    }

    @Test
    public void testInvalidStartStateWithoutSourceDatastore() throws Throwable {
      try {
        ImageCopyService.State startState = new ImageCopyService.State();
        startState.isSelfProgressionDisabled = true;
        startState.image = "image1";
        host.startServiceSynchronously(service, startState);
      } catch (NullPointerException ex) {
        assertThat(ex.getMessage(), is("source datastore not provided"));
      }
    }

    @Test(expectedExceptions = NullPointerException.class,
        expectedExceptionsMessageRegExp = "^destination datastore not provided$")
    public void testInvalidStartStateWithoutDestinationDataStore() throws Throwable {
      ImageCopyService.State startState = new ImageCopyService.State();
      startState.isSelfProgressionDisabled = true;
      startState.image = "image1";
      startState.sourceDataStore = "source-datastore";
      host.startServiceSynchronously(service, startState);
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageHostToHostCopyService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros), is(closeTo(expectedTime, delta)));
    }

    @DataProvider(name = "ExpirationTime")
    public Object[][] getExpirationTime() {
      long expTime = ServiceUtils.computeExpirationTime(TimeUnit.HOURS.toMillis(1));

      return new Object[][]{
          {
              -10L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              0L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              expTime,
              new BigDecimal(expTime),
              new BigDecimal(0)
          }
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageHostToHostCopyService());
      host = TestHost.create(mock(HostClient.class), new ZookeeperHostMonitorSuccessMock());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * Test patch operation with invalid stage update.
     *
     * @param startStage
     * @param transitionStage
     * @param errorMsg
     * @throws Throwable
     */
    @Test(dataProvider = "invalidStageTransitions")
    public void testInvalidStageUpdate(
        TaskState.TaskStage startStage,
        TaskState.TaskStage transitionStage,
        String errorMsg)
        throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(startStage));

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      try {
        host.sendRequestAndWait(patchOp);
      } catch (IllegalStateException ex) {
        assertThat(ex.getMessage(), is(errorMsg));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(startStage));
    }

    @Test
    public void testIgnoreDuplicatedSchedulerPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(TaskState.TaskStage.STARTED));

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = TaskState.TaskStage.STARTED;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState)
          .setReferer(UriUtils.buildUri(host, TaskSchedulerServiceFactory.SELF_LINK + "/test")));

      try {
        host.sendRequestAndWait(patchOp);
        fail("Expected IllegalStateException.");
      } catch (IllegalStateException ex) {
        assertThat(ex.getMessage(), is("Service is not in CREATED stage, ignores patch from TaskSchedulerService"));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED,
              "Can not revert to CREATED from STARTED"},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED,
              "Can not patch anymore when in final stage FINISHED"},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED,
              "Can not patch anymore when in final stage FAILED"},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED,
              "Can not patch anymore when in final stage CANCELLED"},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED,
              "Can not patch anymore when in final stage FINISHED"},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED,
              "Can not patch anymore when in final stage FAILED"},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED,
              "Can not patch anymore when in final stage CANCELLED"},
      };
    }

    /**
     * Test that we can "restart" execution of the current stage by sending a self-patch with the same stage.
     * Test that we can move to the "next" stage by sending a self-patch with a different stage.
     *
     * @param startStage
     * @param transitionStage
     * @throws Throwable
     */
    @Test(dataProvider = "stageTransitions")
    public void testUpdateStage(TaskState.TaskStage startStage, TaskState.TaskStage transitionStage)
        throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(startStage));

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(transitionStage));
    }

    @DataProvider(name = "stageTransitions")
    public Object[][] getStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED}
      };
    }

    @Test
    public void testInvalidPatchUpdateImageField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.image = "new-image-id";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Image cannot be changed."));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.image, is("image1"));
    }

    @Test
    public void testInvalidPatchSourceDataStore() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.sourceDataStore = "new-source";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Source datastore cannot be changed."));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.sourceDataStore, is("source-datastore"));
    }

    @Test
    public void testInvalidPatchDestinationDataStore() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.destinationDataStore = "new-destination";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Destination datastore cannot be changed."));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.destinationDataStore, is("datastore1-inv"));
    }
  }

  /**
   * Tests for service running end to end.
   */
  public class EndToEndTest {
    private TestEnvironment machine;

    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ImageCopyService.State copyTask;
    private ZookeeperHostMonitor zookeeperHostMonitor;

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      cloudStoreHelper = mock(CloudStoreHelper.class);
      // Build input.
      copyTask = new ImageCopyService.State();
      copyTask.image = "WindowsRelease9.0";
      copyTask.sourceDataStore = "datastore0";
      copyTask.destinationDataStore = "datastore1";
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        machine.stop();
      }
    }

    @DataProvider(name = "hostCount")
    public Object[][] getHostCount() {
      return new Object[][]{
          {1},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }

    /**
     * Tests copy success scenarios.
     *
     * @param code Result code return from HostClient.
     * @throws Throwable
     */
    @Test(dataProvider = "copyImageSuccessCode")
    public void testSuccess(int hostCount, CopyImageResultCode code) throws Throwable {
      HostClientMock hostClient = new HostClientMock();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      hostClient.setCopyImageResultCode(code);
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          new Predicate<ImageCopyService.State>() {
            @Override
            public boolean test(ImageCopyService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FINISHED;
            }
          });

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create Patch
                  1.0 + // Scheduler start patch
                  1.0 + // Host and dest data store retrieved
                  1.0   // FINISHED
          ));
    }

    @DataProvider(name = "copyImageSuccessCode")
    public Object[][] getCopyImageSuccessCode() {
      return new Object[][]{
          {1, CopyImageResultCode.OK},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, CopyImageResultCode.OK},
          {1, CopyImageResultCode.DESTINATION_ALREADY_EXIST}
      };
    }

    /**
     * Test success copy scenario when source and destination are the same.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "hostCount")
    public void testSuccessWithSameSourceAndDestination(int hostCount) throws Throwable {
      doReturn(new HostClientMock()).when(hostClientFactory).create();

      // modify start state
      copyTask.destinationDataStore = copyTask.sourceDataStore;

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          new Predicate<ImageCopyService.State>() {
            @Override
            public boolean test(ImageCopyService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FINISHED;
            }
          });

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, is(copyTask.sourceDataStore));
      assertThat(response.host, not(isEmptyOrNullString()));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create Patch
                  1.0 + // Schduler starts service
                  1.0 + // Host is retrieved
                  1.0   // FINISHED
          ));
    }

    @Test(dataProvider = "hostCount")
    public void testFailWithGetHostsForDatastore(int hostCount) throws Throwable {
      doReturn(new HostClientCopyImageErrorMock()).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorGetHostsForDatastoreErrorMock();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          new Predicate<ImageCopyService.State>() {
            @Override
            public boolean test(ImageCopyService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FAILED;
            }
          });

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, is(copyTask.destinationDataStore));
      assertThat(response.host, nullValue());
      assertThat(response.taskInfo.failure.message, containsString("GetHostsForDatastore error"));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create Patch
                  1.0 + // Scheduler start patch
                  1.0   // FAILED
          ));
    }

    /**
     * Test error scenario when HostClient returns error codes.
     *
     * @param code Result code return from HostClient.
     * @throws Throwable
     */
    @Test(dataProvider = "copyImageErrorCode")
    public void testFailWithCopyImageErrorCode(
        int hostCount, CopyImageResultCode code, String exception)
        throws Throwable {
      HostClientMock hostClient = new HostClientMock();
      hostClient.setCopyImageResultCode(code);
      doReturn(hostClient).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          new Predicate<ImageCopyService.State>() {
            @Override
            public boolean test(ImageCopyService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FAILED;
            }
          });

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create Patch
                  1.0 + // Scheduler start patch
                  1.0 + // Host and dest data store retrieval
                  1.0   // FAILED
          ));
    }

    @DataProvider(name = "copyImageErrorCode")
    public Object[][] getCopyImageErrorCode() {
      return new Object[][]{
          {
              1,
              CopyImageResultCode.IMAGE_NOT_FOUND,
              ImageNotFoundException.class.toString()
          },
          {
              TestEnvironment.DEFAULT_MULTI_HOST_COUNT,
              CopyImageResultCode.IMAGE_NOT_FOUND,
              ImageNotFoundException.class.toString()
          },
          {
              1,
              CopyImageResultCode.SYSTEM_ERROR,
              SystemErrorException.class.toString()
          }
      };
    }

    @Test(dataProvider = "hostCount")
    public void testFailWithCopyImageException(int hostCount) throws Throwable {
      doReturn(new HostClientCopyImageErrorMock()).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          new Predicate<ImageCopyService.State>() {
            @Override
            public boolean test(ImageCopyService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FAILED;
            }
          });

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.taskInfo.failure.message, containsString("copyImage error"));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create patch
                  1.0 + // Scheduler start patch
                  1.0 + // Host and dest data store retrieval
                  1.0   // FAILED
          ));
    }
  }
}
