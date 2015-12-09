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
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
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
import com.vmware.photon.controller.host.gen.GetMonitoredImagesResultCode;
import com.vmware.photon.controller.host.gen.StartImageOperationResultCode;
import com.vmware.photon.controller.housekeeper.dcp.mock.CloudStoreHelperMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorGetHostsForDatastoreErrorMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.ZookeeperHostMonitorSuccessMock;
import com.vmware.photon.controller.housekeeper.dcp.mock.hostclient.ErrorMockGetDeletedImages;
import com.vmware.photon.controller.housekeeper.dcp.mock.hostclient.ErrorMockGetInactiveImages;
import com.vmware.photon.controller.housekeeper.dcp.mock.hostclient.ErrorMockStartImageScan;
import com.vmware.photon.controller.housekeeper.dcp.mock.hostclient.ErrorMockStartImageSweep;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.photon.controller.resource.gen.InactiveImageDescriptor;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ImageDatastoreSweeperService}.
 */
public class ImageDatastoreSweeperServiceTest {

  private ImageDatastoreSweeperService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ImageDatastoreSweeperService.State buildValidStartupState() {
    ImageDatastoreSweeperService.State state = new ImageDatastoreSweeperService.State();
    state.isSelfProgressionDisabled = true;
    state.datastore = "datastore-1";
    state.imageCreateWatermarkTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    state.imageDeleteWatermarkTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    return state;
  }

  private ImageDatastoreSweeperService.State buildValidStartupState(
      TaskState.TaskStage stage, ImageDatastoreSweeperService.TaskState.SubStage subStage) {

    ImageDatastoreSweeperService.State state = buildValidStartupState();
    state.taskState = new ImageDatastoreSweeperService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    if (stage == null || subStage == null) {
      return state;
    }

    // update fields needed for state
    switch (stage) {
      case STARTED:
        switch (subStage) {
          case TRIGGER_SCAN:
            state.host = "1.1.1.1";
            // fall through
          case WAIT_FOR_SCAN_COMPLETION:
          case TRIGGER_DELETE:
          case WAIT_FOR_DELETE_COMPLETION:
            break;
        }
        break;
    }

    return state;
  }

  private ImageDatastoreSweeperService.State buildMinimalPatch(
      TaskState.TaskStage stage, ImageDatastoreSweeperService.TaskState.SubStage subStage) {

    ImageDatastoreSweeperService.State state = new ImageDatastoreSweeperService.State();
    state.taskState = new ImageDatastoreSweeperService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    if (stage == null || subStage == null) {
      return state;
    }

    // update fields needed for state
    switch (stage) {
      case STARTED:
        switch (subStage) {
          case TRIGGER_SCAN:
            state.host = "1.1.1.1";
            break;
          case WAIT_FOR_SCAN_COMPLETION:
          case TRIGGER_DELETE:
          case WAIT_FOR_DELETE_COMPLETION:
            break;
        }
        break;
    }

    return state;
  }

  /**
   * Tests for the constructor.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() {
      service = new ImageDatastoreSweeperService();
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
    private TestHost host;
    private ZookeeperHostMonitor zookeeperHostMonitor;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageDatastoreSweeperService());

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
   * Tests for getHostClient.
   */
  public class HostClientTest {
    private TestHost host;
    private HostClient hostClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageDatastoreSweeperService());

      hostClient = mock(HostClient.class);
      host = TestHost.create(hostClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * Test that the hostClient stored in the host is returned.
     */
    @Test
    public void testHostClientIsReturned() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());
      assertThat(service.getHostClient(), is(hostClient));
    }

    /**
     * Test that correct exception is thrown when host does not implement the
     * HostClientProvider interface.
     */
    @Test
    public void testClassCastError() {
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      try {
        service.getHostClient();
        fail("Cast class ServiceHost to HostClientProvider should fail");
      } catch (ClassCastException ex) {
        assertThat(ex.getMessage(), startsWith("com.vmware.xenon.common.ServiceHost"));
      }
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    private TestHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageDatastoreSweeperService());
      host = TestHost.create(mock(HostClient.class), mock(ZookeeperHostMonitor.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDatastoreSweeperService.State savedState = host.getServiceState(ImageDatastoreSweeperService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.taskState.subStage, is(ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO));

      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));

      assertThat(savedState.hostPollInterval, is(ImageDatastoreSweeperService.DEFAULT_HOST_POLL_INTERVAL));
    }

    /**
     * Test service start when a start stage is provided.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ExplicitStartStage")
    public void testExplicitStartStage(
        final TaskState.TaskStage startStage,
        final ImageDatastoreSweeperService.TaskState.SubStage startSubStage,
        final TaskState.TaskStage expectedStage,
        final ImageDatastoreSweeperService.TaskState.SubStage expectedSubStage) throws Throwable {

      ImageDatastoreSweeperService.State startState = buildValidStartupState(startStage, startSubStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageDatastoreSweeperService.State savedState = host.getServiceState(ImageDatastoreSweeperService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
    }

    @DataProvider(name = "ExplicitStartStage")
    public Object[][] getExplicitStartStageTestData() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CREATED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CREATED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CREATED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CREATED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CREATED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},

          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null}
      };
    }

    /**
     * Test service start when a start stage is provided.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidStartStage",
        expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = ".*Invalid subStage.*")
    public void testInvalidStartStage(
        final TaskState.TaskStage startStage,
        final ImageDatastoreSweeperService.TaskState.SubStage startSubStage) throws Throwable {
      ImageDatastoreSweeperService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "InvalidStartStage")
    public Object[][] getInvalidStartStageTestData() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.FAILED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CANCELLED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO}
      };
    }

    /**
     * Test expiration time settings.
     *
     * @param time
     * @param expectedTime
     * @param delta
     * @throws Throwable
     */
    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageDatastoreSweeperService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageDatastoreSweeperService.State savedState = host.getServiceState(ImageDatastoreSweeperService.State.class);
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

    /**
     * Tests that exception is raised if the datastore field is not populated.
     *
     * @param datastore
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidDatastore",
        expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = "datastore cannot be (null|blank)")
    public void testInvalidDatastore(String datastore) throws Throwable {
      ImageDatastoreSweeperService.State startState = buildValidStartupState();
      startState.datastore = datastore;
      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "InvalidDatastore")
    public Object[][] getInvalidDatastoreData() {
      return new Object[][]{
          {null},
          {""}
      };
    }

    /**
     * Tests that exception is raised if the host field is not populated.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = "host cannot be blank")
    public void testInvalidHost() throws Throwable {
      ImageDatastoreSweeperService.State startState = buildValidStartupState();
      startState.host = "";
      host.startServiceSynchronously(service, startState);
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "PositiveFields",
        expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = ".* must be greater than zero")
    public void testPositiveFields(String fieldName, Object value) throws Throwable {
      ImageDatastoreSweeperService.State startState = buildValidStartupState();

      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, value);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "PositiveFields")
    public Object[][] getPositiveFieldsData() {
      return new Object[][]{
          {"scanRate", 0L},
          {"scanRate", -10L},

          {"scanTimeout", 0L},
          {"scanTimeout", -10L},

          {"sweepRate", 0L},
          {"sweepRate", -10L},

          {"sweepTimeout", 0L},
          {"sweepTimeout", -10L},

          {"hostPollInterval", 0},
          {"hostPollInterval", -10},

          {"imageCreateWatermarkTime", 0L},
          {"imageCreateWatermarkTime", -10L},

          {"imageDeleteWatermarkTime", 0L},
          {"imageDeleteWatermarkTime", -10L},
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    private TestHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageDatastoreSweeperService());
      host = TestHost.create(mock(HostClient.class), new ZookeeperHostMonitorSuccessMock(),
          new CloudStoreHelperMock());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * Test patch operation with invalid payload.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = BadRequestException.class)
    public void testInvalidPayload() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody("invalid body");

      host.sendRequestAndWait(op);
    }

    /**
     * Test patch requests that update execution stage to the same stage.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdate")
    public void testValidStageUpdate(
        TaskState.TaskStage initialStage,
        ImageDatastoreSweeperService.TaskState.SubStage initialSubStage,
        TaskState.TaskStage targetStage,
        ImageDatastoreSweeperService.TaskState.SubStage targetSubStage) throws Throwable {

      ImageDatastoreSweeperService.State startState = buildValidStartupState(initialStage, initialSubStage);
      host.startServiceSynchronously(service, startState);

      ImageDatastoreSweeperService.State patchState = buildMinimalPatch(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageDatastoreSweeperService.State savedState = host.getServiceState(ImageDatastoreSweeperService.State.class);
      assertThat(savedState.taskState.stage, is(patchState.taskState.stage));
      assertThat(savedState.taskState.subStage, is(patchState.taskState.subStage));
    }

    @DataProvider(name = "ValidStageUpdate")
    public Object[][] getValidStageUpdateData() throws Throwable {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * Test invalid state transitions.
     */
    @Test(dataProvider = "IllegalStageUpdate")
    public void testIllegalStageUpdate(
        TaskState.TaskStage initialStage,
        ImageDatastoreSweeperService.TaskState.SubStage initialSubStage,
        TaskState.TaskStage targetStage,
        ImageDatastoreSweeperService.TaskState.SubStage targetSubStage) throws Throwable {

      ImageDatastoreSweeperService.State startState = buildValidStartupState(initialStage, initialSubStage);
      host.startServiceSynchronously(service, startState);

      Operation patchOp = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(buildMinimalPatch(targetStage, targetSubStage)));

      try {
        host.sendRequestAndWait(patchOp);
        fail("Transition from " + initialStage + " to " + targetStage + " did not fail.");
      } catch (DcpRuntimeException ignored) {
      }

      ImageDatastoreSweeperService.State savedState = host.getServiceState(ImageDatastoreSweeperService.State.class);
      assertThat(savedState.taskState.stage, is(startState.taskState.stage));
      assertThat(savedState.taskState.subStage, is(startState.taskState.subStage));
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] testIllegalStageUpdateData() throws Throwable {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO,
              null, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              null, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN,
              null, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION,
              null, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE,
              null, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},

          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION,
              null, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FINISHED, null, null, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null, null, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_SCAN},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.TRIGGER_DELETE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null, null, null},
      };
    }

    /**
     * Tests that exception is raised for all fields that are immutable.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ImmutableFields",
        expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = ".* is immutable")
    public void testImmutableFields(String field, Object value) throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDatastoreSweeperService.State patch = buildMinimalPatch(
          TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO);

      Field fieldObj = patch.getClass().getField(field);
      fieldObj.set(patch, value);

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patch);

      host.sendRequestAndWait(op);
    }

    @DataProvider(name = "ImmutableFields")
    private Object[][] getImmutableFieldsData() {
      return new Object[][]{
          {"scanRate", 10L},
          {"scanTimeout", 10L},
          {"sweepRate", 10L},
          {"sweepTimeout", 10L},
          {"parentLink", "/new-link"},
          {"referenceImagesCount", 10},
          {"imageCreateWatermarkTime", 10L},
          {"imageDeleteWatermarkTime", 10L},
          {"datastore", "new-datastore"}
      };
    }

    /**
     * Tests that exception is raised if the scanTimeout field is not a positive integer.
     *
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidHostPollIntervalUpdate",
        expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = "hostPollInterval must be greater than zero")
    public void testInvalidHostPollIntervalUpdate(Integer value) throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDatastoreSweeperService.State patch = buildMinimalPatch(
          TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO);
      patch.hostPollInterval = value;

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patch);

      host.sendRequestAndWait(op);
    }

    @DataProvider(name = "InvalidHostPollIntervalUpdate")
    public Object[][] getInvalidHostPollIntervalData() {
      return new Object[][]{
          {0},
          {-10}
      };
    }

    /**
     * Tests that host cannot be updated to empty value.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = "host cannot be blank")
    public void testUpdatingHostToEmpty() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageDatastoreSweeperService.State patch = buildMinimalPatch(
          TaskState.TaskStage.STARTED, ImageDatastoreSweeperService.TaskState.SubStage.GET_HOST_INFO);
      patch.host = "";

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patch);

      host.sendRequestAndWait(op);
    }
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {
    private static final String PARENT_LINK = "/image-cleaner/id1";

    private TestEnvironment machine;
    private ImageDatastoreSweeperService.State request;

    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ZookeeperHostMonitor zookeeperHostMonitor;

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      doReturn(new HostClientMock()).when(hostClientFactory).create();

      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock();
      cloudStoreHelper = new CloudStoreHelper();

      // Build input.
      request = buildValidStartupState();
      request.isSelfProgressionDisabled = false;
      request.parentLink = PARENT_LINK;
      request.hostPollInterval = 1;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        machine.stop();
      }
    }

    /**
     * Default provider to control host count.
     *
     * @return
     */
    @DataProvider(name = "hostCount")
    public Object[][] getHostCount() {
      return new Object[][]{
          {1},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }

    /**
     * Tests clean success scenarios.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "Success")
    public void testSuccess(int hostCount,
                            double patchCount,
                            int[] refImageParams,
                            int[] iaImageParams,
                            boolean isImageDatastore,
                            int deletedImages) throws Throwable {
      zookeeperHostMonitor = new ZookeeperHostMonitorSuccessMock();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);

      machine.startFactoryServiceSynchronously(
          ImageServiceFactory.class, ImageServiceFactory.SELF_LINK);

      // create reference images
      List<ImageService.State> refImages =
          buildReferenceImages(host, refImageParams[0], refImageParams[1]);

      // adjust request
      request.referenceImagesCount = refImages.size();
      request.isImageDatastore = isImageDatastore;

      // configure host client
      List<InactiveImageDescriptor> inactiveImages =
          buildInactiveImages(refImages, request.imageDeleteWatermarkTime, iaImageParams[0],
              iaImageParams[1], iaImageParams[2], iaImageParams[3]);

      HostClientMock hostClient = new HostClientMock();
      hostClient.setInactiveImages(request.datastore, inactiveImages);
      doReturn(hostClient).when(hostClientFactory).create();

      ImageDatastoreSweeperService.State response = machine.callServiceAndWaitForState(
          ImageDatastoreSweeperServiceFactory.SELF_LINK,
          request,
          ImageDatastoreSweeperService.State.class,
          state -> state.taskState.stage == TaskState.TaskStage.FINISHED
      );

      // check final state
      assertThat(response.datastore, is(request.datastore));
      assertThat(response.host, notNullValue());
      assertThat(response.inactiveImagesCount, is(inactiveImages.size()));
      assertThat(response.deletedImagesCount, is(deletedImages));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(patchCount)
      );

      QueryTask.Query datastoreClause = new QueryTask.Query()
          .setTermPropertyName("replicatedDatastore")
          .setNumericRange(QueryTask.NumericRange.createEqualRange(0L));
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ImageService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause).addBooleanClause(datastoreClause);
      querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = machine.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), lessThanOrEqualTo(deletedImages));
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() throws Throwable {
      return new Object[][]{
          /**
           * Image datastore cleanup
           */
          // 0 (0 tombstoned), 0 unused, 0 extra
          {1, 6.0, new int[]{0, 0}, new int[]{0, 0, 0, 0}, true, 0},
          {3, 6.0, new int[]{0, 0}, new int[]{0, 0, 0, 0}, true, 0},
          // 0 (0 tombstoned), 0 unused, 2 extra (0 newer than timestamp)
          {1, 6.0, new int[]{0, 0}, new int[]{0, 0, 2, 0}, true, 0},
          // 0 (0 tombstoned), 0 unused, 2 extra (1 newer than timestamp)
          {1, 6.0, new int[]{0, 0}, new int[]{0, 0, 2, 1}, true, 0},

          // 5 (0 tombstoned), 0 unused
          {1, 6.0, new int[]{5, 0}, new int[]{0, 0, 0, 0}, true, 0},
          // 5 (2 tombstoned), 0 unused
          {1, 6.0, new int[]{5, 2}, new int[]{0, 0, 0, 0}, true, 0},
          // 5 (2 tombstoned), 3 unused (2:eager, 1:on-demand) (0 newer than watermark)
          {1, 6.0, new int[]{5, 2}, new int[]{3, 0, 0, 0}, true, 2},
          {3, 6.0, new int[]{5, 2}, new int[]{3, 0, 0, 0}, true, 2},
          // 5 (2 tombstoned), 3 unused (2:eager, 1:on-demand) (2 newer then watermark)
          {1, 6.0, new int[]{5, 2}, new int[]{3, 2, 0, 0}, true, 2},
          // 5 (2 tombstoned), 3 unused (2:eager, 1:on-demand) (2 newer then watermark),
          // 4 extra (1 newer than watermark)
          {1, 6.0, new int[]{5, 2}, new int[]{3, 2, 4, 1}, true, 2},

          /**
           * Non-Image datastore cleanup
           */
          // 0 on image datastore, 0 unused
          {1, 6.0, new int[]{0, 0}, new int[]{0, 0, 0, 0}, false, 0},
          {3, 6.0, new int[]{0, 0}, new int[]{0, 0, 0, 0}, false, 0},

          // 5 on image datastore (0 tombstoned), 0 unused
          {1, 6.0, new int[]{5, 0}, new int[]{0, 0, 0, 0}, false, 0},

          // 5 on image datastore (2 tombstoned), 0 unused
          {1, 6.0, new int[]{5, 2}, new int[]{0, 0, 0, 0}, false, 0},

          // 5 on image datastore (2 tombstoned), 4 unused (2:eager, 2:on-demand) (0 newer than watermark), 0 extra
          {1, 6.0, new int[]{5, 2}, new int[]{4, 0, 0, 0}, false, 3},
          {3, 6.0, new int[]{5, 2}, new int[]{4, 0, 0, 0}, false, 3},

          // 5 on image datastore (0 tombstoned), 4 unused (2:eager, 2:on-demand) (0 newer than watermark), 0 extra
          {1, 6.0, new int[]{5, 0}, new int[]{4, 0, 0, 0}, false, 2},

          // 5 on image datastore (0 tombstoned), 4 unused (2:eager, 2:on-demand) (0 newer than watermark),
          // 3 extra (0 newer than watermark)
          {1, 6.0, new int[]{5, 0}, new int[]{4, 0, 3, 0}, false, 5},

          // 5 on image datastore (0 tombstoned), 4 unused (2:eager, 2:on-demand) (0 newer than watermark),
          // 3 extra (1 newer than watermark)
          {1, 6.0, new int[]{5, 0}, new int[]{4, 0, 3, 1}, false, 4},
          {3, 6.0, new int[]{5, 0}, new int[]{4, 0, 3, 1}, false, 4}
      };
    }

    /**
     * Tests call listHostsForStorage to zookeeper client fails.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "ZKMonGetHostsForDatastoreError")
    public void testZKMonGetHostsForDatastoreError(int hostCount, ZookeeperHostMonitor zkHostMonitor)
        throws Throwable {

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zkHostMonitor, hostCount);
      ImageDatastoreSweeperService.State response = machine.callServiceAndWaitForState(
          ImageDatastoreSweeperServiceFactory.SELF_LINK,
          request,
          ImageDatastoreSweeperService.State.class,
          state -> state.taskState.stage == TaskState.TaskStage.FAILED
      );

      // Check response
      assertThat(response.datastore, is(request.datastore));
      assertThat(response.host, nullValue());

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(1.0 +  // START:GET_HOST_INFO
              1.0   // FAILED
          )
      );
    }

    @DataProvider(name = "ZKMonGetHostsForDatastoreError")
    public Object[][] getZKMonGetHostsForDatastoreErrorData() throws Exception {
      return new Object[][]{
          {1, new ZookeeperHostMonitorGetHostsForDatastoreErrorMock()},
          {1, new ZookeeperHostMonitorSuccessMock(0, 0)},
          {3, new ZookeeperHostMonitorSuccessMock(0, 0)}
      };
    }

    /**
     * Test Thrift calls to host fail.
     *
     * @param hostCount
     * @param patchCount
     * @param reason
     * @param hostClient
     * @throws Throwable
     */
    @Test(dataProvider = "HostClientErrors")
    public void testHostClientErrors(int hostCount, double patchCount, String reason, HostClientMock hostClient)
        throws Throwable {

      hostClient.setInactiveImages(request.datastore, new ArrayList<>());
      doReturn(hostClient).when(hostClientFactory).create();

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor, hostCount);
      ImageDatastoreSweeperService.State response = machine.callServiceAndWaitForState(
          ImageDatastoreSweeperServiceFactory.SELF_LINK,
          request,
          ImageDatastoreSweeperService.State.class,
          state -> state.taskState.stage == TaskState.TaskStage.FAILED
      );

      // Check response
      assertThat(reason, response.datastore, is(request.datastore));
      assertThat(reason, response.host, notNullValue());

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          reason,
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(patchCount));
    }

    @DataProvider(name = "HostClientErrors")
    public Object[][] getHostClientErrorsData() {
      return new Object[][]{
          // StartImageScan Failures
          {1, 3.0, "TException", new ErrorMockStartImageScan()},
          {1, 3.0, "SYSTEM_ERROR", new ErrorMockStartImageScan(StartImageOperationResultCode.SYSTEM_ERROR)},
          {1, 3.0, "DATASTORE_NOT_FOUND",
              new ErrorMockStartImageScan(StartImageOperationResultCode.DATASTORE_NOT_FOUND)},
          {1, 3.0, "SCAN_IN_PROGRESS",
              new ErrorMockStartImageScan(StartImageOperationResultCode.SCAN_IN_PROGRESS)},
          {1, 3.0, "SWEEP_IN_PROGRESS",
              new ErrorMockStartImageScan(StartImageOperationResultCode.SWEEP_IN_PROGRESS)},
          {3, 3.0, "TException", new ErrorMockStartImageScan()},
          {3, 3.0, "SYSTEM_ERROR", new ErrorMockStartImageScan(StartImageOperationResultCode.SYSTEM_ERROR)},

          // GetInactiveImages Failures
          {1, 4.0, "TException", new ErrorMockGetInactiveImages()},
          {1, 4.0, "SYSTEM_ERROR", new ErrorMockGetInactiveImages(GetMonitoredImagesResultCode.SYSTEM_ERROR)},
          {1, 4.0, "DATASTORE_NOT_FOUND",
              new ErrorMockGetInactiveImages(GetMonitoredImagesResultCode.DATASTORE_NOT_FOUND)},
          {3, 4.0, "TException", new ErrorMockGetInactiveImages()},
          {3, 4.0, "SYSTEM_ERROR", new ErrorMockGetInactiveImages(GetMonitoredImagesResultCode.SYSTEM_ERROR)},

          // StartImageSweep Failures
          {1, 5.0, "TException", new ErrorMockStartImageSweep()},
          {1, 5.0, "SYSTEM_ERROR", new ErrorMockStartImageSweep(StartImageOperationResultCode.SYSTEM_ERROR)},
          {1, 5.0, "DATASTORE_NOT_FOUND",
              new ErrorMockStartImageSweep(StartImageOperationResultCode.DATASTORE_NOT_FOUND)},
          {1, 5.0, "SCAN_IN_PROGRESS",
              new ErrorMockStartImageSweep(StartImageOperationResultCode.SCAN_IN_PROGRESS)},
          {1, 5.0, "SWEEP_IN_PROGRESS",
              new ErrorMockStartImageSweep(StartImageOperationResultCode.SWEEP_IN_PROGRESS)},
          {3, 5.0, "TException", new ErrorMockStartImageSweep()},
          {3, 5.0, "SYSTEM_ERROR", new ErrorMockStartImageSweep(StartImageOperationResultCode.SYSTEM_ERROR)},

          // GetDeletedImages Failures
          {1, 6.0, "TException", new ErrorMockGetDeletedImages()},
          {1, 6.0, "SYSTEM_ERROR", new ErrorMockGetDeletedImages(GetMonitoredImagesResultCode.SYSTEM_ERROR)},
          {1, 6.0, "DATASTORE_NOT_FOUND",
              new ErrorMockGetDeletedImages(GetMonitoredImagesResultCode.DATASTORE_NOT_FOUND)},
          {3, 6.0, "TException", new ErrorMockGetDeletedImages()},
          {3, 6.0, "SYSTEM_ERROR", new ErrorMockGetDeletedImages(GetMonitoredImagesResultCode.SYSTEM_ERROR)},
      };
    }

    private List<ImageService.State> buildReferenceImages(ServiceHost host, int count, int tombstoned)
        throws Throwable {
      List<ImageService.State> images = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        com.vmware.photon.controller.cloudstore.dcp.entity.ImageService.State state
            = new ImageService.State();
        state.name = "image-" + i;
        state.replicationType = ImageReplicationType.EAGER;
        if (i % 2 == 0) {
          state.replicationType = ImageReplicationType.ON_DEMAND;
        }

        state.state = ImageState.READY;

        if (i < tombstoned) {
          state.state = ImageState.PENDING_DELETE;
        }
        state.totalImageDatastore = 1;
        state.totalDatastore = 1;
        state.replicatedDatastore = 1;

        Operation op = cloudStoreHelper
            .createPost(ImageServiceFactory.SELF_LINK)
            .setBody(state)
            .setCompletion((operation, throwable) -> {
              if (null != throwable) {
                Assert.fail("Failed to create a reference image.");
              }
            });

        Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
        images.add(result.getBody(ImageService.State.class));
      }

      return images;
    }

    private List<InactiveImageDescriptor> buildInactiveImages(
        List<ImageService.State> referenceImages, long timestamp, int count, int countNew, int extra, int extraNew) {
      List<InactiveImageDescriptor> images = new ArrayList<>();
      for (int i = 0; i < Math.min(count, referenceImages.size()); i++) {
        InactiveImageDescriptor image = new InactiveImageDescriptor();
        image.setImage_id(ServiceUtils.getIDFromDocumentSelfLink(referenceImages.get(i).documentSelfLink));
        image.setTimestamp(timestamp + 100);
        if (i >= countNew) {
          image.setTimestamp(timestamp - 100);
        }
        images.add(image);
      }

      for (int i = 0; i < extra; i++) {
        InactiveImageDescriptor image = new InactiveImageDescriptor();
        image.setImage_id("extra-image-" + i);
        image.setTimestamp(timestamp + 100);
        if (i >= extraNew) {
          image.setTimestamp(timestamp - 100);
        }
        images.add(image);
      }

      return images;
    }
  }
}
