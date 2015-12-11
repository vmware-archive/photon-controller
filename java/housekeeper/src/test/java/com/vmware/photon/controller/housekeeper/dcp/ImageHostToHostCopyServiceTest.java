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
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.ImageTransferInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.TransferImageResultCode;
import com.vmware.photon.controller.housekeeper.dcp.mock.CloudStoreHelperMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientTransferImageErrorMock;
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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
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

  private ImageHostToHostCopyService.State buildValidStartupState
      (TaskState.TaskStage stage, ImageHostToHostCopyService.TaskState.SubStage subStage) {
    ImageHostToHostCopyService.State state = buildValidStartupState();
    state.taskInfo = new ImageHostToHostCopyService.TaskState();
    state.host = "host";
    state.taskInfo.stage = stage;
    state.taskInfo.subStage = subStage;

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

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(savedState.taskInfo.subStage, nullValue());
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    @Test
    public void testStartStateWithEmptyTaskInfo() throws Throwable {
      ImageHostToHostCopyService.State startState = buildValidStartupState();
      startState.taskInfo = new ImageHostToHostCopyService.TaskState();
      host.startServiceSynchronously(service, startState);

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(savedState.taskInfo.subStage, nullValue());
    }

    @Test
    public void testStartStateWithCREATEDStage() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(TaskState.TaskStage.CREATED, null));

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
      assertThat(savedState.taskInfo.subStage, nullValue());
    }

    @DataProvider(name = "targetStages")
    public Object[][] getTargetStages() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {TaskState.TaskStage.STARTED, ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null}
      };
    }

    /**
     * Test that start stage is not changed on service start up. This is expected behaviour when
     * initial state is STARTED, FINISHED or FAILED.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "targetStages")
    public void testStartStageIsNotChanged(TaskState.TaskStage targetStage,
                                           ImageHostToHostCopyService.TaskState.SubStage targetSubStage) throws
        Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(targetStage, targetSubStage));

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));
    }


    @Test
    public void testInvalidStartStateWithoutImage() throws Throwable {
      try {
        ImageHostToHostCopyService.State startState = new ImageHostToHostCopyService.State();
        startState.isSelfProgressionDisabled = true;
        host.startServiceSynchronously(service, startState);
      } catch (DcpRuntimeException ex) {
        assertThat(ex.getMessage(), is("image not provided"));
      }
    }

    @Test
    public void testInvalidStartStateWithoutSourceDatastore() throws Throwable {
      try {
        ImageHostToHostCopyService.State startState = new ImageHostToHostCopyService.State();
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
      ImageHostToHostCopyService.State startState = new ImageHostToHostCopyService.State();
      startState.isSelfProgressionDisabled = true;
      startState.image = "image1";
      startState.sourceDataStore = "source-datastore";
      host.startServiceSynchronously(service, startState);
    }

    /**
     * Test start with missing substage in STARTED state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingSubstage() throws Throwable {
      ImageHostToHostCopyService.State state =
          buildValidStartupState(ImageHostToHostCopyService.TaskState.TaskStage.STARTED, null);

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch missing substage");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), containsString("subStage cannot be null"));
      }
    }

    /**
     * Test start with missing host in STARTED state TRANSFER_IMAGE substage.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingHostTransferImageSubstage() throws Throwable {
      ImageHostToHostCopyService.State state =
          buildValidStartupState(ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE);
      state.host = null;

      try {
        host.startServiceSynchronously(service, state);
        fail("Fail to catch host not found");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("host cannot be null"));
      }
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageHostToHostCopyService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
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
      doNothing().when(service).sendRequest(Matchers.any());

      host = TestHost.create(mock(HostClient.class), new ZookeeperHostMonitorSuccessMock(), new CloudStoreHelperMock());
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
          ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS));

      ImageHostToHostCopyService.State patchState = new ImageHostToHostCopyService.State();
      patchState.taskInfo = new ImageHostToHostCopyService.TaskState();
      patchState.taskInfo.stage = TaskState.TaskStage.STARTED;
      patchState.taskInfo.subStage = ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS;

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

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage,
          is(ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS));
    }

    /**
     * Test patch operation with invalid stage update.
     *
     * @param startStage
     * @param startSubStage
     * @param targetStage
     * @param targetSubStage
     * @throws Throwable
     */
    @Test(dataProvider = "invalidStageTransitions")
    public void testInvalidStageUpdate(
        final ImageHostToHostCopyService.TaskState.TaskStage startStage,
        final ImageHostToHostCopyService.TaskState.SubStage startSubStage,
        final ImageHostToHostCopyService.TaskState.TaskStage targetStage,
        final ImageHostToHostCopyService.TaskState.SubStage targetSubStage)
        throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(startStage, startSubStage));

      ImageHostToHostCopyService.State patchState = new ImageHostToHostCopyService.State();
      patchState.taskInfo = new ImageHostToHostCopyService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;


      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      try {
        host.sendRequestAndWait(patchOp);
      } catch (DcpRuntimeException ex) {
      }

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(startStage));
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() throws Throwable {
      return new Object[][]{
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS,
              null,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},

          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE,
              null,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE},

          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.CREATED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FAILED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.CANCELLED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null, null, null},

          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.CREATED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.FAILED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null,
              ImageHostToHostCopyService.TaskState.TaskStage.CANCELLED, null},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, null, null, null},
      };
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
    @Test(dataProvider = "stageTransitions")
    public void testValidUpdateStage(final ImageHostToHostCopyService.TaskState.TaskStage startStage,
                                     final ImageHostToHostCopyService.TaskState.SubStage startSubStage,
                                     final ImageHostToHostCopyService.TaskState.TaskStage targetStage,
                                     final ImageHostToHostCopyService.TaskState.SubStage targetSubStage)
        throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(startStage, startSubStage));

      ImageHostToHostCopyService.State patchState = new ImageHostToHostCopyService.State();
      patchState.taskInfo = new ImageHostToHostCopyService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));
    }

    @DataProvider(name = "stageTransitions")
    public Object[][] getValidStageTransitions() throws Throwable {
      return new Object[][]{
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS,
              ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.RETRIEVE_HOSTS,
              ImageHostToHostCopyService.TaskState.TaskStage.CANCELLED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE,
              ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE,
              ImageHostToHostCopyService.TaskState.TaskStage.FINISHED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE,
              ImageHostToHostCopyService.TaskState.TaskStage.FAILED, null},
          {ImageHostToHostCopyService.TaskState.TaskStage.STARTED,
              ImageHostToHostCopyService.TaskState.SubStage.TRANSFER_IMAGE,
              ImageHostToHostCopyService.TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test
    public void testInvalidPatchUpdateImageField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageHostToHostCopyService.State patchState = new ImageHostToHostCopyService.State();
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

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.image, is("image1"));
    }

    @Test
    public void testInvalidPatchSourceDataStore() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageHostToHostCopyService.State patchState = new ImageHostToHostCopyService.State();
      patchState.sourceDataStore = "new-source";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("Source datastore cannot be changed."));
      }

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.sourceDataStore, is("source-datastore"));
    }

    @Test
    public void testInvalidPatchDestinationDataStore() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageHostToHostCopyService.State patchState = new ImageHostToHostCopyService.State();
      patchState.destinationDataStore = "new-destination";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Exception expected.");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("Destination datastore cannot be changed."));
      }

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.destinationDataStore, is("datastore1-inv"));
    }

    @Test
    public void testValidPatchHosts() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageHostToHostCopyService.State patchState = new ImageHostToHostCopyService.State();
      patchState.host = "new-host";
      patchState.destinationHost = new ServerAddress("new-destination-host", 0);

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patch);

      ImageHostToHostCopyService.State savedState = host.getServiceState(ImageHostToHostCopyService.State.class);
      assertThat(savedState.host, is("new-host"));
      assertThat(savedState.destinationHost.getHost(), is("new-destination-host"));
      assertThat(savedState.destinationHost.getPort(), is(0));
    }
  }

  /**
   * Tests for service running end to end.
   */
  public class EndToEndTest {
    private TestEnvironment machine;

    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ImageHostToHostCopyService.State copyTask;
    private ZookeeperHostMonitor zookeeperHostMonitor;

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      cloudStoreHelper = mock(CloudStoreHelper.class);
      // Build input.
      copyTask = new ImageHostToHostCopyService.State();
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

    @DataProvider(name = "transferImageSuccessCode")
    public Object[][] getTransferImageSuccessCode() {
      return new Object[][]{
          {1, TransferImageResultCode.OK},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT, TransferImageResultCode.OK},
      };
    }

    /**
     * Tests copy success scenarios.
     *
     * @param code Result code return from HostClient.
     * @throws Throwable
     */
    @Test(dataProvider = "transferImageSuccessCode")
    public void testSuccess(int hostCount, TransferImageResultCode code) throws Throwable {
      HostClientMock hostClient = new HostClientMock();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      hostClient.setTransferImageResultCode(code);
      doReturn(hostClient).when(hostClientFactory).create();

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createHostService("datastore0");
      createHostService("datastore1");

      // Call Service.
      ImageHostToHostCopyService.State response = machine.callServiceAndWaitForState(
          ImageHostToHostCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageHostToHostCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, is(copyTask.destinationDataStore));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.destinationHost, notNullValue());

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create Patch
                  1.0 + // Scheduler start patch
                  1.0   // FINISHED
          ));
    }

    @Test(dataProvider = "hostCount")
    public void testFailWithNoHostForSourceDatastore(int hostCount) throws Throwable {
      doReturn(new HostClientTransferImageErrorMock()).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createHostService("datastore1");

      // Call Service.
      ImageHostToHostCopyService.State response = machine.callServiceAndWaitForState(
          ImageHostToHostCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageHostToHostCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, is(copyTask.destinationDataStore));
      assertThat(response.host, nullValue());
      assertThat(response.destinationHost, nullValue());
      assertThat(response.taskInfo.failure.message, containsString("No host found for source " +
          "image datastore datastore0"));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create patch
                  1.0 + // Scheduler start patch
                  1.0   // FAILED
          ));
    }

    @Test(dataProvider = "hostCount")
    public void testFailWithNoHostForDestinationDatastore(int hostCount) throws Throwable {
      doReturn(new HostClientTransferImageErrorMock()).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createHostService("datastore0");

      // Call Service.
      ImageHostToHostCopyService.State response = machine.callServiceAndWaitForState(
          ImageHostToHostCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageHostToHostCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, is(copyTask.destinationDataStore));
      assertThat(response.host, nullValue());
      assertThat(response.destinationHost, nullValue());
      assertThat(response.taskInfo.failure.message, containsString("No host found for destination " +
          "image datastore datastore1"));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create patch
                  1.0 + // Scheduler start patch
                  1.0   // FAILED
          ));
    }

    @Test(dataProvider = "hostCount")
    public void testFailWithTransferImageException(int hostCount) throws Throwable {
      doReturn(new HostClientTransferImageErrorMock()).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createHostService("datastore0");
      createHostService("datastore1");

      // Call Service.
      ImageHostToHostCopyService.State response = machine.callServiceAndWaitForState(
          ImageHostToHostCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageHostToHostCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, is(copyTask.destinationDataStore));
      assertThat(response.host, not(isEmptyOrNullString()));
      assertThat(response.taskInfo.failure.message, containsString("transferImage error"));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 + // Create patch
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
    @Test(dataProvider = "transferImageErrorCode")
    public void testFailWithTransferImageErrorCode(
        int hostCount, TransferImageResultCode code, String exception)
        throws Throwable {
      HostClientMock hostClient = new HostClientMock();
      hostClient.setTransferImageResultCode(code);
      doReturn(hostClient).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          hostCount,
          ZookeeperHostMonitorSuccessMock.DATASTORE_COUNT_DEFAULT);

      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      createHostService("datastore0");
      createHostService("datastore1");

      // Call Service.
      ImageHostToHostCopyService.State response = machine.callServiceAndWaitForState(
          ImageHostToHostCopyServiceFactory.SELF_LINK,
          copyTask,
          ImageHostToHostCopyService.State.class,
          (state) -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Check response.
      assertThat(response.image, is(copyTask.image));
      assertThat(response.sourceDataStore, is(copyTask.sourceDataStore));
      assertThat(response.destinationDataStore, is(copyTask.destinationDataStore));
      assertThat(response.host, not(isEmptyOrNullString()));

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

    @DataProvider(name = "transferImageErrorCode")
    public Object[][] getTransferImageErrorCode() {
      return new Object[][]{
          {
              1,
              TransferImageResultCode.TRANSFER_IN_PROGRESS,
              ImageTransferInProgressException.class.toString()
          },
          {
              1,
              TransferImageResultCode.SYSTEM_ERROR,
              SystemErrorException.class.toString()
          }
      };
    }

    private HostService.State createHostService(String reportedImageDatastore) throws Throwable {
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
      state.reportedImageDatastores = new HashSet<>();
      state.reportedImageDatastores.add(reportedImageDatastore);

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
  }
}
