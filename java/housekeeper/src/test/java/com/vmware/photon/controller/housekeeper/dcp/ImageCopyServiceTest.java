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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.ImageNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.host.gen.CopyImageResultCode;
import com.vmware.photon.controller.housekeeper.dcp.mock.CloudStoreHelperMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientCopyImageErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetHostsForDatastoreErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorSuccessMock;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.mockito.Matchers;
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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ImageCopyService}.
 */
public class
    ImageCopyServiceTest {

  private static final Logger logger = LoggerFactory.getLogger(ImageCopyServiceTest.class);
  private TestHost host;
  private ImageCopyService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ImageCopyService.State buildValidStartupState() {
    ImageCopyService.State state = new ImageCopyService.State();
    state.isSelfProgressionDisabled = true;
    state.image = "image1";
    state.sourceImageDataStoreName = "source-datastore";
    state.destinationDataStoreId = "datastore1-inv";

    return state;
  }

  private ImageCopyService.State buildValidStartupState(
      ImageCopyService.TaskState.TaskStage stage,
      ImageCopyService.TaskState.SubStage subStage) {
    ImageCopyService.State state = buildValidStartupState();
    state.taskInfo = new ImageCopyService.TaskState();
    state.taskInfo.stage = stage;
    state.taskInfo.subStage = subStage;
    state.host = "host";

    return state;
  }

  private ImageCopyService.State buildValidStartupState(TaskState.TaskStage stage) {
    ImageCopyService.State state = buildValidStartupState();
    state.taskInfo = new ImageCopyService.TaskState();
    state.taskInfo.stage = stage;

    return state;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageCopyService());
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
      service = spy(new ImageCopyService());
    }

    @Test(expectedExceptions = ClassCastException.class,
        expectedExceptionsMessageRegExp = "^.*ServiceHost.*cannot be cast to.*ZookeeperHostMonitorProvider$")
    public void testClassCastError() {
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      service.getZookeeperHostMonitor();
    }
  }

  /**
   * Test getHostClient.
   */
  public class GetHostClientTest {
    private ImageCopyService.State state;
    private HostClient hostClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClient = mock(HostClient.class);
      host = TestHost.create(hostClient);

      state = buildValidStartupState();
      state.host = "host-ip";

      service = spy(new ImageCopyService());
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
      service = spy(new ImageCopyService());
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
      assertThat(savedState.taskInfo.subStage, nullValue());
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @Test
    public void testStartStateWithEmptyTaskInfo() throws Throwable {
      ImageCopyService.State startState = buildValidStartupState();
      startState.taskInfo = new ImageCopyService.TaskState();
      host.startServiceSynchronously(service, startState);

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(savedState.taskInfo.subStage, nullValue());
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
          {TaskState.TaskStage.STARTED, ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {TaskState.TaskStage.STARTED, ImageCopyService.TaskState.SubStage.COPY_IMAGE},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null}
      };
    }

    /**
     * Test that start stage is not changed on service start up. This is expected behaviour when
     * initial state is STARTED (with sub stages), FINISHED or FAILED.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "targetStages")
    public void testStartStageIsNotChanged(TaskState.TaskStage targetStage, ImageCopyService.TaskState.SubStage
        targetSubStage) throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(targetStage, targetSubStage));

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));
    }

    @Test
    public void testInvalidStartStateWithoutImage() throws Throwable {
      try {
        ImageCopyService.State startState = new ImageCopyService.State();
        startState.isSelfProgressionDisabled = true;
        host.startServiceSynchronously(service, startState);
      } catch (DcpRuntimeException ex) {
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
      } catch (DcpRuntimeException ex) {
        assertThat(ex.getMessage(), is("source datastore not provided"));
      }
    }

    @Test(expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = "^destination datastore not provided$")
    public void testInvalidStartStateWithoutDestinationDataStore() throws Throwable {
      ImageCopyService.State startState = new ImageCopyService.State();
      startState.isSelfProgressionDisabled = true;
      startState.image = "image1";
      startState.sourceImageDataStoreName = "source-datastore";
      host.startServiceSynchronously(service, startState);
    }

    /**
     * Test start with missing substage in STARTED state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingSubstage() throws Throwable {
      ImageCopyService.State state = buildValidStartupState(ImageCopyService.TaskState.TaskStage.STARTED);

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing substage");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), containsString("subStage cannot be null"));
      }
    }

    /**
     * Test start with missing host in STARTED state COPY_IMAGE substage.
     *
     * @throws Throwable
     */
    @Test
    public void testCopyImageSubstageMissingHost() throws Throwable {
      ImageCopyService.State state =
          buildValidStartupState(ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE);
      state.host = null;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch host not found");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("host not found"));
      }
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageCopyService.State startState = buildValidStartupState();
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
      service = spy(new ImageCopyService());
      doNothing().when(service).sendRequest(Matchers.any());
      host = TestHost.create(mock(HostClient.class), new ZookeeperHostMonitorSuccessMock(),
          new CloudStoreHelperMock());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    @Test
    public void testIgnoreDuplicatedSchedulerPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(TaskState.TaskStage.STARTED,
          ImageCopyService.TaskState.SubStage.RETRIEVE_HOST));

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.taskInfo = new ImageCopyService.TaskState();
      patchState.taskInfo.stage = TaskState.TaskStage.STARTED;
      patchState.taskInfo.subStage = ImageCopyService.TaskState.SubStage.RETRIEVE_HOST;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState)
          .setReferer(UriUtils.buildUri(host, TaskSchedulerServiceFactory.SELF_LINK + "/test")));

      try {
        host.sendRequestAndWait(patchOp);
        fail("Expected IllegalStateException.");
      } catch (DcpRuntimeException ex) {
        assertThat(ex.getMessage(), is("Service is not in CREATED stage, ignores patch from TaskSchedulerService"));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage,
          is(ImageCopyService.TaskState.SubStage.RETRIEVE_HOST));
    }

    /**
     * Test that we can "restart" execution of the current stage by sending a self-patch with the same stage.
     * Test that we can move to the "next" stage by sending a self-patch with a different stage.
     *
     * @param startStage
     * @param startSubStage
     * @param targetStage
     * @param targetSubStage
     * @throws Throwable
     */
    /**
     * This test verifies that legal stage transitions succeed.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        final ImageCopyService.TaskState.TaskStage startStage,
        final ImageCopyService.TaskState.SubStage startSubStage,
        final ImageCopyService.TaskState.TaskStage targetStage,
        final ImageCopyService.TaskState.SubStage targetSubStage
    ) throws Throwable {
      final int dataStoreCount = 0;
      doReturn(new ZookeeperHostMonitorSuccessMock(0, 1, dataStoreCount)).when(service).getZookeeperHostMonitor();

      ImageCopyService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.taskInfo = new ImageCopyService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));

    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdatesData() throws Throwable {
      return new Object[][]{
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST,
              ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST,
              ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST,
              ImageCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST,
              ImageCopyService.TaskState.TaskStage.FAILED, null},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST,
              ImageCopyService.TaskState.TaskStage.CANCELLED, null},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE,
              ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE,
              ImageCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE,
              ImageCopyService.TaskState.TaskStage.FAILED, null},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE,
              ImageCopyService.TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that errors occur on illegal state transitions.
     *
     * @param startStage
     * @param startSubStage
     * @param targetStage
     * @param targetSubStage
     * @throws Throwable
     */
    @Test(dataProvider = "IllegalStageUpdate")
    public void testIllegalStageUpdate(
        final ImageCopyService.TaskState.TaskStage startStage,
        final ImageCopyService.TaskState.SubStage startSubStage,
        final ImageCopyService.TaskState.TaskStage targetStage,
        final ImageCopyService.TaskState.SubStage targetSubStage)
        throws Throwable {
      ImageCopyService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.taskInfo = new ImageCopyService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Transition from " + startStage + ":" + startSubStage +
            " to " + targetStage + ":" + targetSubStage + " " + "did not fail.");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] getIllegalStageUpdateData() throws Throwable {
      return new Object[][]{
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST,
              ImageCopyService.TaskState.TaskStage.FINISHED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST,
              null,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},

          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE,
              ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE,
              ImageCopyService.TaskState.TaskStage.FINISHED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE},
          {ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE,
              null,
              ImageCopyService.TaskState.SubStage.COPY_IMAGE},

          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.CREATED, null},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.STARTED, null},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.FINISHED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.FAILED, null},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.FAILED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null,
              ImageCopyService.TaskState.TaskStage.CANCELLED, null},
          {ImageCopyService.TaskState.TaskStage.FINISHED, null, null, null},

          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.CREATED, null},
          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.STARTED, null},
          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.STARTED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.FINISHED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.FAILED, null},
          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.FAILED,
              ImageCopyService.TaskState.SubStage.RETRIEVE_HOST},
          {ImageCopyService.TaskState.TaskStage.FAILED, null,
              ImageCopyService.TaskState.TaskStage.CANCELLED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null, null, null},
      };
    }

    @Test
    public void testInvalidPatchUpdateParentLink() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.parentLink = "new-parentLink";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("ParentLink cannot be changed."));
      }
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
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("Image cannot be changed."));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.image, is("image1"));
    }

    @Test
    public void testInvalidPatchSourceDataStore() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.sourceImageDataStoreName = "new-source";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("Source datastore cannot be changed."));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.sourceImageDataStoreName, is("source-datastore"));
    }

    @Test
    public void testInvalidPatchDestinationDataStore() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageCopyService.State patchState = new ImageCopyService.State();
      patchState.destinationDataStoreId = "new-destination";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("Destination datastore cannot be changed."));
      }

      ImageCopyService.State savedState = host.getServiceState(ImageCopyService.State.class);
      assertThat(savedState.destinationDataStoreId, is("datastore1-inv"));
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
      copyTask.sourceImageDataStoreName = "source-datastore-name";
      copyTask.destinationDataStoreId = "destination-datastore-id";
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
      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      ImageService.State createdImageState = createNewImageEntity();
      int initialReplicatedDatastoreCount = createdImageState.replicatedDatastore;
      copyTask.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      createHostService();
      createDatastoreService();

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      //Check Image Service replicatedDatastore counts
      createdImageState = machine.getServiceState(createdImageState.documentSelfLink, ImageService.State.class);
      if (code.equals(CopyImageResultCode.OK)) {
        assertThat(createdImageState.replicatedDatastore, is(initialReplicatedDatastoreCount + 1));
      } else {
        assertThat(createdImageState.replicatedDatastore, is(initialReplicatedDatastoreCount));
      }

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceImageDataStoreName, is(copyTask.sourceImageDataStoreName));
      assertThat(response.destinationDataStoreId, not(isEmptyOrNullString()));
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
      copyTask.destinationDataStoreId = "source-datastore-id";

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createDatastoreService();

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceImageDataStoreName, is(copyTask.sourceImageDataStoreName));
      assertThat(response.sourceImageDataStoreId, is(copyTask.sourceImageDataStoreId));
      assertThat(response.destinationDataStoreId, is("source-datastore-id"));
      assertThat(response.host, isEmptyOrNullString());

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create Patch
                  1.0 + // Scheduler starts service
                  1.0   // FINISHED
          ));
    }

    @Test(dataProvider = "hostCount")
    public void testFinishWithNoHostsForDatastore(int hostCount) throws Throwable {
      doReturn(new HostClientCopyImageErrorMock()).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorGetHostsForDatastoreErrorMock();

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createDatastoreService();

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceImageDataStoreName, is(copyTask.sourceImageDataStoreName));
      assertThat(response.destinationDataStoreId, is(copyTask.destinationDataStoreId));
      assertThat(response.host, nullValue());
      assertThat(response.taskInfo.failure.message, containsString("No host found between source image datastore " +
          "source-datastore-id and destination datastore " + copyTask.destinationDataStoreId));

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

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createHostService();
      createDatastoreService();

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceImageDataStoreName, is(copyTask.sourceImageDataStoreName));
      assertThat(response.destinationDataStoreId, not(isEmptyOrNullString()));
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

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createHostService();
      createDatastoreService();

      // Call Service.
      ImageCopyService.State response = machine.callServiceAndWaitForState(
          ImageCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceImageDataStoreName, is(copyTask.sourceImageDataStoreName));
      assertThat(response.destinationDataStoreId, not(isEmptyOrNullString()));
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

    private com.vmware.photon.controller.cloudstore.dcp.entity.ImageService.State createNewImageEntity()
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

      Operation op = cloudStoreHelper
          .createPost(com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a image in cloud store.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(ImageService.State.class);
    }

    private HostService.State createHostService() throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);

      machine.startFactoryServiceSynchronously(
          HostServiceFactory.class,
          HostServiceFactory.SELF_LINK);

      HostService.State state = new HostService.State();
      state.state = HostState.READY;
      state.hostAddress = "0.0.0.0";
      state.userName = "test-name";
      state.password = "test-password";
      state.usageTags = new HashSet<>();
      state.usageTags.add(UsageTag.CLOUD.name());
      state.reportedDatastores = new HashSet<>();
      state.reportedDatastores.add(copyTask.destinationDataStoreId);
      state.reportedImageDatastores = new HashSet<>();
      state.reportedImageDatastores.add("source-datastore-id");

      Operation op = cloudStoreHelper
          .createPost(HostServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a host in cloud store.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(HostService.State.class);
    }

    private DatastoreService.State createDatastoreService() throws Throwable {
      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);

      machine.startFactoryServiceSynchronously(
          DatastoreServiceFactory.class,
          DatastoreServiceFactory.SELF_LINK);

      DatastoreService.State state = new DatastoreService.State();
      state.id = "source-datastore-id";
      state.name = copyTask.sourceImageDataStoreName;
      state.isImageDatastore = true;
      state.type = "EXT3";
      state.documentSelfLink = "/source-datastore-id";

      Operation op = cloudStoreHelper
          .createPost(DatastoreServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a datastore document in cloud store.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(DatastoreService.State.class);
    }
  }
}
