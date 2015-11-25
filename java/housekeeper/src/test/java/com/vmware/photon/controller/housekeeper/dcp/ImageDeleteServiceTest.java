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

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.InvalidRefCountException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.host.gen.DeleteImageResultCode;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientDeleteImageErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetHostsForDatastoreErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorSuccessMock;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.photon.controller.resource.gen.ImageInfo;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.inject.Injector;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Tests {@link ImageDeleteService}.
 */
public class ImageDeleteServiceTest {

  private static final Logger logger = LoggerFactory.getLogger(ImageDeleteServiceTest.class);
  private static final String configFilePath = "/config.yml";

  private Injector injector;
  private TestHost host;
  private ImageDeleteService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ImageDeleteService.State buildValidStartupState() {
    ImageDeleteService.State state = new ImageDeleteService.State();
    state.isSelfProgressionDisabled = true;
    state.image = "image1";
    state.dataStore = "datastore1-inv";

    return state;
  }

  private ImageDeleteService.State buildValidStartupState(TaskState.TaskStage stage) {
    ImageDeleteService.State state = buildValidStartupState();
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
      service = spy(new ImageDeleteService());
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
   * Tests getZookeeperHostMonitor.
   */
  public class GetZookeeperHostMonitorTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageDeleteService());
    }

    @Test
    public void testClassCastError() {
      doReturn(mock(ServiceHost.class)).when(service).getHost();

      try {
        service.getZookeeperHostMonitor();
        fail("Cast class ServiceHost to ZookeeperHostMonitorProvider should fail");
      } catch (ClassCastException ex) {
      }
    }
  }

  /**
   * Test getHostClient.
   */
  public class GetHostClientTest {
    private ImageDeleteService.State state;
    private HostClient hostClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClient = mock(HostClient.class);
      host = TestHost.create(hostClient);

      state = buildValidStartupState();
      state.host = "host-ip";

      service = spy(new ImageDeleteService());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    @Test(
        expectedExceptions = ClassCastException.class,
        expectedExceptionsMessageRegExp =
            "^.*ServiceHost.*cannot be cast to " +
                "com\\.vmware\\.photon\\.controller\\.common\\.clients\\.HostClientProvider$")
    public void testClassCastError() throws Throwable {
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      service.getHostClient(state);
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageDeleteService());
      host = TestHost.create(mock(HostClient.class), mock(ZookeeperHostMonitor.class));
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

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(savedState.setTombstoneFlag.booleanValue(), is(true));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @Test
    public void testStartStateWithEmptyTaskInfo() throws Throwable {
      ImageDeleteService.State startState = buildValidStartupState();
      startState.taskInfo = new TaskState();
      host.startServiceSynchronously(service, startState);

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
    }

    @Test
    public void testStartStateWithSetTombstoneFlag() throws Throwable {
      ImageDeleteService.State stateWithSetTombstoneFlag = buildValidStartupState();
      stateWithSetTombstoneFlag.setTombstoneFlag = new Boolean(false);
      host.startServiceSynchronously(service, stateWithSetTombstoneFlag);

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
    }

    @Test
    public void testStartStateWithCREATEDStage() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(TaskState.TaskStage.CREATED));

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
    }

    @DataProvider(name = "targetStages")
    public Object[][] getTargetStages() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
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

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(targetStage));
    }

    @Test
    public void testInvalidStartStateWithoutImage() throws Throwable {
      try {
        ImageDeleteService.State startState = new ImageDeleteService.State();
        startState.isSelfProgressionDisabled = true;
        host.startServiceSynchronously(service, startState);
      } catch (DcpRuntimeException ex) {
        assertThat(ex.getMessage(), is("image not provided"));
      }
    }

    @Test
    public void testInvalidStartStateWithoutDatastore() throws Throwable {
      try {
        ImageDeleteService.State startState = new ImageDeleteService.State();
        startState.isSelfProgressionDisabled = true;
        startState.image = "image1";
        host.startServiceSynchronously(service, startState);
      } catch (DcpRuntimeException ex) {
        assertThat(ex.getMessage(), is("datastore not provided"));
      }
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageDeleteService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
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
      service = spy(new ImageDeleteService());
      host = TestHost.create(mock(HostClient.class), mock(ZookeeperHostMonitor.class));
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

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      try {
        host.sendRequestAndWait(patchOp);
      } catch (DcpRuntimeException ex) {
        assertThat(ex.getMessage(), is(errorMsg));
      }

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.taskInfo.stage, is(startStage));
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
      doReturn(new ZookeeperHostMonitorSuccessMock()).when(service).getZookeeperHostMonitor();

      host.startServiceSynchronously(service, buildValidStartupState(startStage));

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.taskInfo = new TaskState();
      patchState.taskInfo.stage = transitionStage;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.taskInfo.stage, is(transitionStage));
    }

    @DataProvider(name = "stageTransitions")
    public Object[][] getStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED}
      };
    }

    @Test
    public void testIgnoreDuplicatedSchedulerPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(TaskState.TaskStage.STARTED));

      ImageDeleteService.State patchState = new ImageDeleteService.State();

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState)
          .setReferer(UriUtils.buildUri(host, TaskSchedulerServiceFactory.SELF_LINK + "/test"));
      try {
        host.sendRequestAndWait(patch);
        fail("Expected IllegalStateException.");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("Service is not in CREATED stage, ignores patch from TaskSchedulerService"));
      }
    }

    @Test
    public void testInvalidPatchUpdateParentLink() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.parentLink = "new-parentLink";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("parentLink cannot be changed."));
      }
    }

    @Test
    public void testInvalidPatchUpdateImageWatermarkTime() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.imageWatermarkTime = 500L;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("imageWatermarkTime cannot be changed."));
      }
    }

    @Test
    public void testInvalidPatchUpdateImageField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.image = "new-image-id";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("image cannot be changed."));
      }

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.image, is("image1"));
    }

    @Test
    public void testInvalidPatchDataStore() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.dataStore = "new-datastore";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("dataStore cannot be changed."));
      }

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.dataStore, is("datastore1-inv"));
    }

    @Test
    public void testInvalidPatchSetTombstoneFlag() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.setTombstoneFlag = new Boolean(false);

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("setTombstoneFlag cannot be changed."));
      }

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.setTombstoneFlag.booleanValue(), is(true));
    }

    @Test
    public void testApplyImageCreatedTime() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDeleteService.State patchState = new ImageDeleteService.State();
      patchState.imageCreatedTime = 500L;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      Operation res = host.sendRequestAndWait(patch);
      assertThat(res.getStatusCode(), is(200));

      ImageDeleteService.State savedState = host.getServiceState(ImageDeleteService.State.class);
      assertThat(savedState.imageCreatedTime, is(patchState.imageCreatedTime));
    }
  }

  /**
   * Tests for service running end to end.
   */
  public class EndToEndTest {
    private TestEnvironment machine;

    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ZookeeperHostMonitor zookeeperHostMonitor;
    private ImageDeleteService.State deleteTask;

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      cloudStoreHelper = mock(CloudStoreHelper.class);
      zookeeperHostMonitor = mock(ZookeeperHostMonitor.class);

      // Build input.
      deleteTask = new ImageDeleteService.State();
      deleteTask.taskInfo = new TaskState();
      deleteTask.taskInfo.stage = TaskState.TaskStage.STARTED;
      deleteTask.image = "WindowsRelease9.0";
      deleteTask.dataStore = "datastore1";
      deleteTask.setTombstoneFlag = new Boolean(false);

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
     * Tests delete success scenarios.
     *
     * @param code Result code return from HostClient.
     * @throws Throwable
     */
    @Test(dataProvider = "SuccessNoImageWatermarkTime")
    public void testSuccessNoImageWatermarkTime(int hostCount, DeleteImageResultCode code) throws Throwable {
      HostClientMock hostClient = new HostClientMock();
      hostClient.setDeleteImageResultCode(code);
      doReturn(hostClient).when(hostClientFactory).create();
      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock();
      cloudStoreHelper = new CloudStoreHelper();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Create image entity in cloudstore
      ImageService.State createdImageState = createNewImageEntity();
      int initialReplicatedDatastoreCount = createdImageState.replicatedDatastore;

      deleteTask.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

      // Call Service.
      ImageDeleteService.State response = machine.callServiceAndWaitForState(
          ImageDeleteServiceFactory.SELF_LINK,
          deleteTask,
          ImageDeleteService.State.class,
          new Predicate<ImageDeleteService.State>() {
            @Override
            public boolean test(ImageDeleteService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FINISHED;
            }
          }
      );

      //Check Image Service replicatedDatastore counts
      createdImageState = machine.getServiceState(createdImageState.documentSelfLink, ImageService.State.class);
      if (code.equals(DeleteImageResultCode.OK)) {
        assertThat(createdImageState.replicatedDatastore, is(initialReplicatedDatastoreCount - 1));
      } else {
        assertThat(createdImageState.replicatedDatastore, is(initialReplicatedDatastoreCount));
      }


      // Check response.
      assertThat(response.image, is(deleteTask.image));
      assertThat(response.dataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.setTombstoneFlag.booleanValue(), is(false));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(
              1.0 + // Start patch
                  1.0 + // Host and dest data store retrieved
                  1.0   // FINISHED
          )
      );
    }

    @DataProvider(name = "SuccessNoImageWatermarkTime")
    public Object[][] getSuccessNoImageWatermarkTimeData() {
      return new Object[][]{
          {1, DeleteImageResultCode.OK},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, DeleteImageResultCode.OK},
          {1, DeleteImageResultCode.IMAGE_IN_USE},
          {1, DeleteImageResultCode.IMAGE_NOT_FOUND},
      };
    }

    /**
     * Tests delete success scenarios.
     *
     * @param code Result code return from HostClient.
     * @throws Throwable
     */
    @Test(dataProvider = "SuccessImageOlderThanImageWatermarkTime")
    public void testSuccessImageOlderThanImageWatermarkTime(
        int hostCount, DeleteImageResultCode code) throws Throwable {
      ZookeeperHostMonitorSuccessMock zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock();

      ImageInfo info = new ImageInfo();
      info.setCreated_time(DateTime.now().toString());

      HostClientMock hostClient = new HostClientMock();
      hostClient.setDeleteImageResultCode(code);
      hostClient.setImageInfo(info);
      doReturn(hostClient).when(hostClientFactory).create();
      cloudStoreHelper = new CloudStoreHelper();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Create image entity in cloudstore
      ImageService.State createdImageState = createNewImageEntity();
      int initialReplicatedDatastoreCount = createdImageState.replicatedDatastore;

      deleteTask.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

      // Call Service.
      deleteTask.imageWatermarkTime = DateTime.now().getMillis();
      ImageDeleteService.State response = machine.callServiceAndWaitForState(
          ImageDeleteServiceFactory.SELF_LINK,
          deleteTask,
          ImageDeleteService.State.class,
          new Predicate<ImageDeleteService.State>() {
            @Override
            public boolean test(ImageDeleteService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FINISHED;
            }
          }
      );

      //Check Image Service replicatedDatastore counts
      createdImageState = machine.getServiceState(createdImageState.documentSelfLink, ImageService.State.class);
      if (code.equals(DeleteImageResultCode.OK)) {
        assertThat(createdImageState.replicatedDatastore, is(initialReplicatedDatastoreCount - 1));
      } else {
        assertThat(createdImageState.replicatedDatastore, is(initialReplicatedDatastoreCount));
      }

      // Check response.
      assertThat(response.image, is(deleteTask.image));
      assertThat(response.dataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.setTombstoneFlag.booleanValue(), is(false));
      assertThat(response.imageWatermarkTime, greaterThan(response.imageCreatedTime));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(
              1.0 + // Start patch
                  1.0 + // Host and dest data store retrieved
                  1.0 + // Image create time retrieved
                  1.0   // FINISHED
          )
      );
    }

    @DataProvider(name = "SuccessImageOlderThanImageWatermarkTime")
    public Object[][] getSuccessImageOlderThanImageWatermarkTimeData() {
      return new Object[][]{
          {1, DeleteImageResultCode.OK},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, DeleteImageResultCode.OK},
          {1, DeleteImageResultCode.IMAGE_IN_USE},
          {1, DeleteImageResultCode.IMAGE_NOT_FOUND},
      };
    }

    /**
     * Tests delete success scenarios.
     *
     * @param code Result code return from HostClient.
     * @throws Throwable
     */
    @Test(dataProvider = "SuccessImageNewerThanImageWatermarkTime")
    public void testSuccessImageNewerThanImageWatermarkTime(
        int hostCount, DeleteImageResultCode code) throws Throwable {
      DateTime timeStamp = DateTime.now();

      ZookeeperHostMonitorSuccessMock zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock();

      ImageInfo info = new ImageInfo();
      info.setCreated_time(timeStamp.plusMinutes(2).toString());

      HostClientMock hostClient = new HostClientMock();
      hostClient.setDeleteImageResultCode(code);
      hostClient.setImageInfo(info);
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      deleteTask.imageWatermarkTime = timeStamp.getMillis();
      ImageDeleteService.State response = machine.callServiceAndWaitForState(
          ImageDeleteServiceFactory.SELF_LINK,
          deleteTask,
          ImageDeleteService.State.class,
          new Predicate<ImageDeleteService.State>() {
            @Override
            public boolean test(ImageDeleteService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FINISHED;
            }
          }
      );

      // Check response.
      assertThat(response.image, is(deleteTask.image));
      assertThat(response.dataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.setTombstoneFlag.booleanValue(), is(false));
      assertThat(response.imageWatermarkTime, lessThan(response.imageCreatedTime));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(
              1.0 + // Start patch
                  1.0 + // Host and dest data store retrieved
                  1.0 + // Image create time retrieved
                  1.0   // FINISHED
          )
      );
    }

    @DataProvider(name = "SuccessImageNewerThanImageWatermarkTime")
    public Object[][] getSuccessImageNewerThanImageWatermarkTimeData() {
      return new Object[][]{
          {1, DeleteImageResultCode.OK},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, DeleteImageResultCode.OK},
          {1, DeleteImageResultCode.IMAGE_IN_USE},
          {1, DeleteImageResultCode.IMAGE_NOT_FOUND},
      };
    }

    @Test(dataProvider = "hostCount")
    public void testFailWithGetHostsForDatastore(int hostCount) throws Throwable {
      ZookeeperHostMonitorGetHostsForDatastoreErrorMock
          zookeeperHostMonitor = new ZookeeperHostMonitorGetHostsForDatastoreErrorMock();
      doReturn(new HostClientDeleteImageErrorMock()).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageDeleteService.State response = machine.callServiceAndWaitForState(
          ImageDeleteServiceFactory.SELF_LINK,
          deleteTask,
          ImageDeleteService.State.class,
          new Predicate<ImageDeleteService.State>() {
            @Override
            public boolean test(ImageDeleteService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FAILED;
            }
          }
      );

      // Check response.
      assertThat(response.image, is(deleteTask.image));
      assertThat(response.dataStore, is(deleteTask.dataStore));
      assertThat(response.host, nullValue());
      assertThat(response.setTombstoneFlag.booleanValue(), is(false));
      assertThat(response.taskInfo.failure.message, containsString("GetHostsForDatastore error"));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(
              1.0 + // Start patch
                  1.0   // FAILED
          )
      );
    }

    /**
     * Test error scenario when HostClient returns error codes.
     *
     * @param code Result code return from HostClient.
     * @throws Throwable
     */
    @Test(dataProvider = "deleteImageErrorCode")
    public void testFailWithDeleteImageErrorCode(
        int hostCount, DeleteImageResultCode code, String exception)
        throws Throwable {
      ZookeeperHostMonitorSuccessMock zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock();
      HostClientMock hostClient = new HostClientMock();
      hostClient.setDeleteImageResultCode(code);
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageDeleteService.State response = machine.callServiceAndWaitForState(
          ImageDeleteServiceFactory.SELF_LINK,
          deleteTask,
          ImageDeleteService.State.class,
          new Predicate<ImageDeleteService.State>() {
            @Override
            public boolean test(ImageDeleteService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FAILED;
            }
          }
      );

      // Check response.
      assertThat(response.image, is(deleteTask.image));
      assertThat(response.dataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.setTombstoneFlag.booleanValue(), is(false));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(
              1.0 + // Start patch
                  1.0 + // Host and dest data store retrieval
                  1.0   // FAILED
          )
      );
    }

    @DataProvider(name = "deleteImageErrorCode")
    public Object[][] getDeleteImageErrorCode() {
      return new Object[][]{
          {
              1,
              DeleteImageResultCode.SYSTEM_ERROR,
              SystemErrorException.class.toString()
          },
          {
              TestEnvironment.DEFAULT_MULTI_HOST_COUNT,
              DeleteImageResultCode.SYSTEM_ERROR,
              SystemErrorException.class.toString()
          },
          {
              1,
              DeleteImageResultCode.INVALID_REF_COUNT_FILE,
              InvalidRefCountException.class.toString()
          }
      };
    }

    /**
     * Test error scenario where HostClient call throws an exception.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "hostCount")
    public void testFailWithDeleteImageException(int hostCount) throws Throwable {
      doReturn(new HostClientDeleteImageErrorMock()).when(hostClientFactory).create();
      ZookeeperHostMonitorSuccessMock zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      // Call Service.
      ImageDeleteService.State response = machine.callServiceAndWaitForState(
          ImageDeleteServiceFactory.SELF_LINK,
          deleteTask,
          ImageDeleteService.State.class,
          new Predicate<ImageDeleteService.State>() {
            @Override
            public boolean test(ImageDeleteService.State state) {
              return state.taskInfo.stage == TaskState.TaskStage.FAILED;
            }
          }
      );

      // Check response.
      assertThat(response.image, is(deleteTask.image));
      assertThat(response.dataStore, not(isEmptyOrNullString()));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.setTombstoneFlag.booleanValue(), is(false));
      assertThat(response.taskInfo.failure.message, containsString("deleteImage error"));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(
              1.0 + // Start patch
                  1.0 + // Host and dest data store retrieval
                  1.0   // FAILED
          )
      );
    }

    private ImageService.State createNewImageEntity()
        throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);

      machine.startFactoryServiceSynchronously(
          com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory.class,
          com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory.SELF_LINK);

      com.vmware.photon.controller.cloudstore.dcp.entity.ImageService.State state
          = new com.vmware.photon.controller.cloudstore.dcp.entity.ImageService.State();
      state.name = "image-1";
      state.replicationType = ImageReplicationType.EAGER;
      state.state = ImageState.READY;
      state.totalDatastore = 1;
      state.replicatedDatastore = 1;

      Operation op = cloudStoreHelper
          .createPost(com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a image in clod store.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(ImageService.State.class);
    }
  }
}
