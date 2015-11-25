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
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientDeleteImageErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetAllDatastoresErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetHostsForDatastoreErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorSuccessMock;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import org.hamcrest.Matchers;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Tests {@link ImageRemoverService}.
 */
public class ImageRemoverServiceTest {

  private TestHost host;
  private ImageRemoverService service;

  private ImageRemoverService.State buildValidStartupState() {
    return buildValidStartupState(null, null);
  }

  private ImageRemoverService.State buildValidStartupState(
      ImageRemoverService.TaskState.TaskStage stage,
      ImageRemoverService.TaskState.SubStage subStage) {
    ImageRemoverService.State state = new ImageRemoverService.State();
    state.image = "image-id";

    state.isSelfProgressionDisabled = true;
    state.queryPollDelay = 50;

    if (stage != null) {
      state.taskInfo = new ImageRemoverService.TaskState();
      state.taskInfo.stage = stage;
      state.taskInfo.subStage = subStage;
    }

    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new ImageRemoverService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
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
      service = spy(new ImageRemoverService());
      host = TestHost.create(mock(HostClient.class), mock(ZookeeperHostMonitor.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test start of service with minimal valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMinimalStartState() throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState();
      startState.queryPollDelay = null;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, Matchers.is(ImageRemoverService.TaskState.TaskStage.STARTED));
      assertThat(savedState.taskInfo.subStage, is(ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES));
      assertThat(savedState.queryPollDelay, is(10000));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    /**
     * Test service start when a start stage is provided.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ExplicitStartStage")
    public void testExplicitStartStage(
        final ImageRemoverService.TaskState.TaskStage startStage,
        final ImageRemoverService.TaskState.SubStage startSubStage,
        final ImageRemoverService.TaskState.TaskStage expectedStage,
        final ImageRemoverService.TaskState.SubStage expectedSubStage
    ) throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState(startStage, startSubStage);
      startState.dataStoreCount = 0;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(expectedStage));
      assertThat(savedState.taskInfo.subStage, is(expectedSubStage));
      if (savedState.taskInfo.stage == ImageRemoverService.TaskState.TaskStage.STARTED &&
          savedState.taskInfo.subStage == ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION) {
        assertThat(savedState.failedOrCanceledDeletes, nullValue());
        assertThat(savedState.finishedDeletes, nullValue());
      }

    }

    @DataProvider(name = "ExplicitStartStage")
    public Object[][] getExplicitStartStageTestData() {
      return new Object[][]{
          {ImageRemoverService.TaskState.TaskStage.CREATED, null,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.CREATED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.CREATED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},

          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null,
              ImageRemoverService.TaskState.TaskStage.FAILED, null},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.CANCELLED, null}
      };
    }

    /**
     * Test service start when an invalid start stage is provided.
     *
     * @param stage
     * @param subStage
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidStartStage")
    public void testInvalidStartStage(
        final ImageRemoverService.TaskState.TaskStage stage,
        final ImageRemoverService.TaskState.SubStage subStage
    ) throws Throwable {
      try {
        host.startServiceSynchronously(service, buildValidStartupState(stage, subStage));
        fail("service start did not fail when 'stage' was invalid");
      } catch (DcpRuntimeException ex) {
        assertThat(ex.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "InvalidStartStage")
    public Object[][] getInvalidStartStageTestData() {
      return new Object[][]{
          {ImageRemoverService.TaskState.TaskStage.STARTED, null},

          {ImageRemoverService.TaskState.TaskStage.FINISHED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageRemoverService.TaskState.TaskStage.FAILED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.FAILED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageRemoverService.TaskState.TaskStage.CANCELLED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION}
      };
    }

    /**
     * Test that queryPollDelay value is not change on startup if present.
     *
     * @throws Throwable
     */
    @Test
    public void testQueryPollDelayIsNotChanged() throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState();
      startState.queryPollDelay = 500;
      host.startServiceSynchronously(service, startState);

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.queryPollDelay, is(startState.queryPollDelay));
    }

    /**
     * Test that invalid values for queryPollDelay are not accepted.
     *
     * @param delay
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidQueryPollDelay")
    public void testInvalidQueryPollDelay(Integer delay) throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState();
      startState.queryPollDelay = delay;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not when 'queryPollDelay' <= 0");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("queryPollDelay needs to be greater than 0"));
      }
    }

    @DataProvider(name = "InvalidQueryPollDelay")
    public Object[][] getInvalidQueryPollDelay() {
      return new Object[][]{
          {-10}, {0}
      };
    }

    /**
     * Test service start with missing image identifier in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingImage() throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState();
      startState.image = null;

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'image' was null");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("image cannot be null"));
      }
    }

    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
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
      service = spy(new ImageRemoverService());
      host = TestHost.create(mock(HostClient.class), mock(ZookeeperHostMonitor.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation with invalid payload.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchBody() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      Operation op = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody("invalid body"));

      try {
        host.sendRequestAndWait(op);
        fail("handlePatch did not throw exception on invalid patch");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(),
            startsWith("Unparseable JSON body: java.lang.IllegalStateException: Expected BEGIN_OBJECT"));
      }
    }

    /**
     * Tests that an error is returned for a patch that tries to update the image field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchUpdateImageField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.image = "new-image-id";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Changing image via a patch should fail");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("image field cannot be updated in a patch"));
      }

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.image, is("image-id"));
    }

    /**
     * This test verifies that legal stage transitions succeed.
     *
     * @param startStage
     * @param targetStage
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        final ImageRemoverService.TaskState.TaskStage startStage,
        final ImageRemoverService.TaskState.SubStage startSubStage,
        final ImageRemoverService.TaskState.TaskStage targetStage,
        final ImageRemoverService.TaskState.SubStage targetSubStage
    ) throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState(startStage, startSubStage);
      startState.dataStoreCount = 0;

      host.startServiceSynchronously(service, startState);

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.taskInfo = new ImageRemoverService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.taskInfo.stage, is(targetStage));
      assertThat(savedState.taskInfo.subStage, is(targetSubStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdatesData() throws Throwable {
      return new Object[][]{
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.FAILED, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.CANCELLED, null},

          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.FAILED, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that errors occur on illegal state transitions.
     *
     * @param startStage
     * @param targetStage
     * @throws Throwable
     */
    @Test(dataProvider = "IllegalStageUpdate")
    public void testIllegalStageUpdate(
        final ImageRemoverService.TaskState.TaskStage startStage,
        final ImageRemoverService.TaskState.SubStage startSubStage,
        final ImageRemoverService.TaskState.TaskStage targetStage,
        final ImageRemoverService.TaskState.SubStage targetSubStage
    ) throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState(startStage, startSubStage);
      startState.dataStoreCount = 0;

      host.startServiceSynchronously(service, startState);

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.taskInfo = new ImageRemoverService.TaskState();
      patchState.taskInfo.stage = targetStage;
      patchState.taskInfo.subStage = targetSubStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Transition from " + startStage + " to " + targetStage + "did not fail.");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] getIllegalStageUpdateData() throws Throwable {
      return new Object[][]{
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.CREATED, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              null, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.FINISHED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.FAILED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES,
              ImageRemoverService.TaskState.TaskStage.CANCELLED,
              ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},

          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.CREATED, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              null, null},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.FINISHED,
              ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.FAILED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION,
              ImageRemoverService.TaskState.TaskStage.CANCELLED,
              ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},

          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.CREATED, null},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.FINISHED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.FINISHED,
              ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.FAILED, null},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null,
              ImageRemoverService.TaskState.TaskStage.CANCELLED, null},
          {ImageRemoverService.TaskState.TaskStage.FINISHED, null, null, null},

          {ImageRemoverService.TaskState.TaskStage.FAILED, null, ImageRemoverService.TaskState.TaskStage.CREATED, null},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null,
              ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null,
              ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null, ImageRemoverService.TaskState.TaskStage.FAILED, null},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null,
              ImageRemoverService.TaskState.TaskStage.FAILED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null, ImageRemoverService.TaskState.TaskStage.FAILED,
              ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null,
              ImageRemoverService.TaskState.TaskStage.CANCELLED, null},
          {ImageRemoverService.TaskState.TaskStage.FAILED, null, null, null},

          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.CREATED, null},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.STARTED,
              ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.STARTED,
              ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.FAILED, null},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.CANCELLED, null},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.CANCELLED,
              ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null,
              ImageRemoverService.TaskState.TaskStage.CANCELLED,
              ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, null, null, null},
      };
    }

    /**
     * Test that a patch with dataStoreCount field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateDataStoreCountField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.dataStoreCount = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.dataStoreCount, is(10));
    }

    /**
     * Test patch with an invalid value for the dataStoreCount field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidDataStoreCountField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.dataStoreCount = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("validation did not fail when dataStoreCount was updated to a value < 0");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("dataStoreCount needs to be >= 0"));
      }
    }

    /**
     * Test validation of dataStoreCount not being null in AWAIT_COMPLETION stage.
     *
     * @throws Throwable
     */
    @Test
    public void testNullDataStoreCountField() throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState(
          ImageRemoverService.TaskState.TaskStage.STARTED,
          ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION);

      try {
        host.startServiceSynchronously(service, startState);
        fail("validation did not fail when dataStoreCount was 'null' in STARTED:AWAIT_COMPLETION stage");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("dataStoreCount cannot be null"));
      }
    }

    /**
     * Test that a patch with finishedDeletes field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateFinishedDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.finishedDeletes = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.finishedDeletes, is(10));
    }

    /**
     * Test patch with an invalid value for the finishedDeletes field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidFinishedDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.finishedDeletes = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("validation did not fail when finishedDeletes was updated to a value < 0");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("finishedDeletes needs to be >= 0"));
      }
    }

    /**
     * Test that a patch with failedOrCanceledDeletes field updates the state successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateFailedOrCanceledDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.failedOrCanceledDeletes = 10;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patch);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.failedOrCanceledDeletes, is(10));
    }

    /**
     * Test patch with an invalid value for the failedOrCanceledDeletes field.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidFailedOrCanceledDeletesField() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.failedOrCanceledDeletes = -1;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("validation did not fail when failedOrCanceledDeletes was updated to a value < 0");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("failedOrCanceledDeletes needs to be >= 0"));
      }
    }

    /**
     * This function starts a new service in the TRIGGER_DELETES state and verifies
     * that the appropriate number of new ImageDeleteService instances are started.
     *
     * @throws Throwable
     */
    @Test
    public void testImageDeleteServicesCreated() throws Throwable {
      final int dataStoreCount = 3;

      doReturn(new ZookeeperHostMonitorSuccessMock(1, 1, dataStoreCount)).when(service).getZookeeperHostMonitor();

      ImageRemoverService.State startState = buildValidStartupState(
          ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES);

      host.startServiceSynchronously(service, startState);
      host.startServiceSynchronously(
          new ImageDeleteServiceFactory(), null, ImageDeleteServiceFactory.SELF_LINK);

      // trigger the stage execution
      ImageRemoverService.State patchState = new ImageRemoverService.State();
      patchState.taskInfo = new ImageRemoverService.TaskState();
      patchState.taskInfo.stage = ImageRemoverService.TaskState.TaskStage.STARTED;
      patchState.taskInfo.subStage = ImageRemoverService.TaskState.SubStage.TRIGGER_DELETES;
      patchState.dataStoreCount = dataStoreCount;

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState));

      host.sendRequestAndWait(patchOp);

      // check that services were created
      QueryTask query = QueryTask.create(
          QueryTaskUtils.buildChildServiceQuerySpec(
              service.getSelfLink(),
              ImageDeleteService.State.class)
      )
          .setDirect(true);

      QueryTask response = host.waitForQuery(query,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() >= dataStoreCount;
            }
          }
      );
      assertThat(response.results.documentLinks.size(), is(dataStoreCount));
    }

    /**
     * This function tests that the service goes into the FINISHED state if all delete
     * tasks finish successfully.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "FinishedData")
    public void testFinished(
        Integer dataStoreCount,
        Integer finishedDeletes,
        Integer failedOrCanceledDeletes,
        final ImageRemoverService.TaskState.TaskStage finalStage,
        String failureMessage) throws Throwable {
      ImageRemoverService.State startState = buildValidStartupState(
          ImageRemoverService.TaskState.TaskStage.STARTED, ImageRemoverService.TaskState.SubStage.AWAIT_COMPLETION);

      for (int i = 0; failedOrCanceledDeletes != null && i < failedOrCanceledDeletes; i++) {
        if (i % 2 == 0) {
          buildImageDeleteService(ImageRemoverService.TaskState.TaskStage.FAILED);
        } else {
          buildImageDeleteService(ImageRemoverService.TaskState.TaskStage.CANCELLED);
        }
      }

      for (int i = 0; finishedDeletes != null && i < finishedDeletes; i++) {
        buildImageDeleteService(ImageRemoverService.TaskState.TaskStage.FINISHED);
      }

      startState.isSelfProgressionDisabled = false;
      startState.dataStoreCount = dataStoreCount;

      host.startServiceSynchronously(service, startState);

      host.waitForState(ImageRemoverService.State.class,
          new Predicate<ImageRemoverService.State>() {
            @Override
            public boolean test(ImageRemoverService.State state) {
              return state.taskInfo.stage == finalStage;
            }
          }
      );

      ImageRemoverService.State savedState = host.getServiceState(ImageRemoverService.State.class);
      assertThat(savedState.taskInfo, notNullValue());
      assertThat(savedState.taskInfo.stage, is(finalStage));
      assertThat(savedState.taskInfo.subStage, nullValue());
      if (failureMessage != null) {
        assertThat(savedState.taskInfo.failure.message, containsString(failureMessage));
      }
    }

    @DataProvider(name = "FinishedData")
    Object[][] getFinishedData() {
      return new Object[][]{
          {2, 2, null, ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {2, 2, 0, ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {500, 500, 0, ImageRemoverService.TaskState.TaskStage.FINISHED, null},
          {3, 2, 1, ImageRemoverService.TaskState.TaskStage.FAILED,
              "Removal failed: 2 deletes succeeded, 1 deletes failed"},
          {500, 499, 1, ImageRemoverService.TaskState.TaskStage.FAILED,
              "Removal failed: 499 deletes succeeded, 1 deletes failed"},
          {2, 0, 2, ImageRemoverService.TaskState.TaskStage.FAILED,
              "Removal failed: 0 deletes succeeded, 2 deletes failed"},
          {2, null, 2, ImageRemoverService.TaskState.TaskStage.FAILED,
              "Removal failed: 0 deletes succeeded, 2 deletes failed"}
      };
    }

    /**
     * Starts an ImageDeleteServices as a child of the ImageRemoverService
     * instance under test.
     *
     * @param stage Completion stage for ImageDeleteService instances.
     * @throws Throwable
     */
    private void buildImageDeleteService(ImageRemoverService.TaskState.TaskStage stage) throws Throwable {
      ImageDeleteService.State task = new ImageDeleteService.State();
      task.parentLink = TestHost.SERVICE_URI;
      task.image = "image1";
      task.dataStore = "data-store-id";
      task.isSelfProgressionDisabled = true;

      task.taskInfo = new com.vmware.xenon.common.TaskState();
      task.taskInfo.stage = stage;
      if (stage == ImageRemoverService.TaskState.TaskStage.FAILED) {
        task.taskInfo.failure = new ServiceErrorResponse();
        task.taskInfo.failure.message = String.format("ImageDeleteService failed");
      }

      String documentLink = String.format("/image-deleters/%s", UUID.randomUUID().toString());
      host.startServiceSynchronously(new ImageDeleteService(), task, documentLink);
    }
  }

  /**
   * Tests getZookeeperHostMonitor.
   */
  public class GetZookeeperHostMonitorTest {
    ZookeeperHostMonitor zookeeperHostMonitor;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageRemoverService());

      zookeeperHostMonitor = mock(ZookeeperHostMonitor.class);
      host = TestHost.create(mock(HostClient.class), zookeeperHostMonitor);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * Test that the zookeeperHostMonitor stored in the host is returned.
     */
    @Test
    public void testZookeeperHostMonitorIsReturned() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());
      assertThat(service.getZookeeperHostMonitor(), is(zookeeperHostMonitor));
    }

    /**
     * Test that correct exception is thrown when host does not implement the
     * ZookeeperHostMonitorProvider interface.
     */
    @Test
    public void testClassCastError() {
      doReturn(mock(ServiceHost.class)).when(service).getHost();

      try {
        service.getZookeeperHostMonitor();
        fail("Cast class ServiceHost to ZookeeperHostMonitorProvider should fail");
      } catch (ClassCastException ex) {
        assertThat(ex.getMessage(), startsWith("com.vmware.xenon.common.ServiceHost"));
      }
    }
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {
    private TestEnvironment machine;
    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ZookeeperHostMonitor zookeeperHostMonitor;

    private ImageRemoverService.State request;
    private int dataStoreCount;

    @BeforeMethod
    public void setup() throws Throwable {
      // setup defaults
      dataStoreCount = 3;
      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock(
          ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
          ZookeeperHostMonitorSuccessMock.HOST_COUNT_DEFAULT,
          dataStoreCount);

      hostClientFactory = mock(HostClientFactory.class);
      cloudStoreHelper = mock(CloudStoreHelper.class);
      doReturn(new HostClientMock()).when(hostClientFactory).create();

      // Build input.
      request = buildValidStartupState();
      request.isSelfProgressionDisabled = false;
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

    @Test(dataProvider = "hostCount")
    public void testSuccess(int hostCount) throws Throwable {
      cloudStoreHelper = new CloudStoreHelper();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      ImageService.State createdImageState = createNewImageEntity();
      request.image = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

      //Call Service.
      ImageRemoverService.State response = machine.callServiceAndWaitForState(ImageRemoverServiceFactory.SELF_LINK,
          request,
          ImageRemoverService.State.class,
          new Predicate<ImageRemoverService.State>() {
            @Override
            public boolean test(ImageRemoverService.State state) {
              return state.taskInfo.stage == ImageRemoverService.TaskState.TaskStage.FINISHED;
            }
          }
      );

      // Check response
      assertThat(response.dataStoreCount, is(dataStoreCount));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +       // START:TRIGGER_DELETES
                  1.0 +   // START:AWAIT_COMPLETION
                  1.0     // FINISHED
          )
      );
    }

    @Test(dataProvider = "hostCount")
    public void testListAllDatastoreFail(int hostCount) throws Throwable {
      zookeeperHostMonitor = new ZookeeperHostMonitorGetAllDatastoresErrorMock();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      //Call Service.
      ImageRemoverService.State response = machine.callServiceAndWaitForState(
          ImageRemoverServiceFactory.SELF_LINK,
          request,
          ImageRemoverService.State.class,
          new Predicate<ImageRemoverService.State>() {
            @Override
            public boolean test(ImageRemoverService.State state) {
              return state.taskInfo.stage == ImageRemoverService.TaskState.TaskStage.FAILED;
            }
          }
      );

      // Check response.
      assertThat(response.dataStoreCount, nullValue());

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(
              1.0 +     // START:TRIGGER_DELETES
                  1.0   // FAILED
          )
      );
    }

    @Test(dataProvider = "hostCount")
    public void testGetHostsForDatastoreFail(int hostCount) throws Throwable {
      zookeeperHostMonitor = new ZookeeperHostMonitorGetHostsForDatastoreErrorMock(dataStoreCount);
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      //Call Service.
      ImageRemoverService.State response = machine.callServiceAndWaitForState(
          ImageRemoverServiceFactory.SELF_LINK,
          request,
          ImageRemoverService.State.class,
          new Predicate<ImageRemoverService.State>() {
            @Override
            public boolean test(ImageRemoverService.State state) {
              return state.taskInfo.stage == ImageRemoverService.TaskState.TaskStage.FAILED;
            }
          }
      );

      // Check response.
      assertThat(response.dataStoreCount, is(dataStoreCount));
      assertTrue(response.finishedDeletes == null || response.finishedDeletes == 0);
      assertThat(response.failedOrCanceledDeletes, is(dataStoreCount));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +       // START:CREATE_BATCHES
                  1.0 +   // START:AWAIT_COMPLETION
                  1.0 +   // At least one query patch
                  1.0     // FAILED
          )
      );
    }

    @Test(dataProvider = "hostCount")
    public void testDeleteImageFail(int hostCount) throws Throwable {
      doReturn(new HostClientDeleteImageErrorMock()).when(hostClientFactory).create();
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);

      //Call Service.
      ImageRemoverService.State response = machine.callServiceAndWaitForState(
          ImageRemoverServiceFactory.SELF_LINK,
          request,
          ImageRemoverService.State.class,
          new Predicate<ImageRemoverService.State>() {
            @Override
            public boolean test(ImageRemoverService.State state) {
              return state.taskInfo.stage == ImageRemoverService.TaskState.TaskStage.FAILED;
            }
          }
      );

      // Check response.
      assertThat(response.dataStoreCount, is(dataStoreCount));
      assertTrue(response.finishedDeletes == null || response.finishedDeletes == 0);
      assertThat(response.failedOrCanceledDeletes, is(dataStoreCount));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          greaterThanOrEqualTo(
              1.0 +       // START:CREATE_BATCHES
                  1.0 +   // START:AWAIT_COMPLETION
                  1.0 +   // At least one query patche
                  1.0     // FAILED
          )
      );
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
      state.replicatedDatastore = 1;

      Operation op = cloudStoreHelper
          .createPost(com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a reference image.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(com.vmware.photon.controller.cloudstore.dcp.entity.ImageService.State.class);
    }
  }
}
