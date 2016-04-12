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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Tests {@link ImageCleanerTriggerService}.
 */
public class ImageCleanerTriggerServiceTest {

  private TestHost host;
  private ImageCleanerTriggerService service;

  private ImageCleanerTriggerService.State buildValidStartupState() {
    return buildValidStartupState(null);
  }

  private ImageCleanerTriggerService.State buildValidStartupState(ImageCleanerTriggerService.ExecutionState
                                                                      executionState) {
    ImageCleanerTriggerService.State state = new ImageCleanerTriggerService.State();

    if (executionState != null) {
      state.executionState = executionState;
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
      service = new ImageCleanerTriggerService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageCleanerTriggerService());
      host = TestHost.create(mock(HostClient.class));
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
      Operation startOp = host.startServiceSynchronously(service, buildValidStartupState());
      assertThat(startOp.getStatusCode(), is(200));

      ImageCleanerTriggerService.State savedState = host.getServiceState(ImageCleanerTriggerService.State.class);
      assertThat(savedState.executionState, notNullValue());
      assertThat(savedState.executionState, is(ImageCleanerTriggerService.ExecutionState.RUNNING));
    }

    /**
     * Test service start when a start stage is provided.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ExplicitStartStage")
    public void testExplicitStartStage(
        final ImageCleanerTriggerService.ExecutionState startExecutionState,
        final ImageCleanerTriggerService.ExecutionState expectedExecutionState) throws Throwable {
      ImageCleanerTriggerService.State startState = buildValidStartupState(startExecutionState);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageCleanerTriggerService.State savedState = host.getServiceState(ImageCleanerTriggerService.State.class);
      assertThat(savedState.executionState, is(expectedExecutionState));
    }

    @DataProvider(name = "ExplicitStartStage")
    public Object[][] getExplicitStartStageTestData() {
      return new Object[][]{
          {null, ImageCleanerTriggerService.ExecutionState.RUNNING},
          {ImageCleanerTriggerService.ExecutionState.RUNNING, ImageCleanerTriggerService.ExecutionState.RUNNING},
          {ImageCleanerTriggerService.ExecutionState.STOPPED, ImageCleanerTriggerService.ExecutionState.STOPPED}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageCleanerTriggerService());
      host = TestHost.create(mock(HostClient.class));
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
     * This test verifies that legal stage transitions succeed.
     *
     * @param startExecutionState
     * @param expectedExecutionState
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        final ImageCleanerTriggerService.ExecutionState startExecutionState,
        final ImageCleanerTriggerService.ExecutionState expectedExecutionState) throws Throwable {
      ImageCleanerTriggerService.State startState = buildValidStartupState(startExecutionState);
      host.startServiceSynchronously(service, startState);

      ImageCleanerTriggerService.State patchState = new ImageCleanerTriggerService.State();
      patchState.executionState = expectedExecutionState;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ImageCleanerTriggerService.State savedState = host.getServiceState(ImageCleanerTriggerService.State.class);
      assertThat(savedState.executionState, is(expectedExecutionState));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdatesData() throws Throwable {
      return new Object[][]{
          {null, ImageCleanerTriggerService.ExecutionState.RUNNING},
          {ImageCleanerTriggerService.ExecutionState.RUNNING, ImageCleanerTriggerService.ExecutionState.RUNNING},
          {ImageCleanerTriggerService.ExecutionState.RUNNING, ImageCleanerTriggerService.ExecutionState.STOPPED},
          {ImageCleanerTriggerService.ExecutionState.STOPPED, ImageCleanerTriggerService.ExecutionState.RUNNING},
          {ImageCleanerTriggerService.ExecutionState.STOPPED, ImageCleanerTriggerService.ExecutionState.STOPPED}
      };
    }

    /**
     * This test verifies that errors occur on illegal state transitions.
     *
     * @param startExecutionState
     * @param expectedExecutionState
     * @throws Throwable
     */
    @Test(dataProvider = "IllegalStageUpdate")
    public void testIllegalStageUpdate(
        final ImageCleanerTriggerService.ExecutionState startExecutionState,
        final ImageCleanerTriggerService.ExecutionState expectedExecutionState) throws Throwable {
      ImageCleanerTriggerService.State startState = buildValidStartupState(startExecutionState);
      host.startServiceSynchronously(service, startState);

      ImageCleanerTriggerService.State patchState = new ImageCleanerTriggerService.State();
      patchState.executionState = expectedExecutionState;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Transition from " + startExecutionState + " to " + expectedExecutionState + "did not fail.");
      } catch (BadRequestException e) {
        assertEquals(e.getMessage(), "ExecutionState cannot be null.");
      }
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] getIllegalStageUpdateData() throws Throwable {
      return new Object[][]{
          {null, null},
          {ImageCleanerTriggerService.ExecutionState.RUNNING, null},
          {ImageCleanerTriggerService.ExecutionState.STOPPED, null},
      };
    }
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {
    private TestEnvironment machine;
    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ServiceConfigFactory serviceConfigFactory;
    private NsxClientFactory nsxClientFactory;

    private ImageCleanerTriggerService.State request;

    @BeforeMethod
    public void setup() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      cloudStoreHelper = mock(CloudStoreHelper.class);
      doReturn(new HostClientMock()).when(hostClientFactory).create();
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      nsxClientFactory = mock(NsxClientFactory.class);

      // Build input.
      request = buildValidStartupState();
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
    public void testTriggerActivationSuccess(int hostCount) throws Throwable {
      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory,
          serviceConfigFactory, nsxClientFactory, hostCount);

      //Call Service.
      ImageCleanerTriggerService.State response = machine.checkServiceIsResponding(
          machine.getTriggerCleanerServiceUri(),
          ImageCleanerTriggerService.State.class, new Predicate<ImageCleanerTriggerService.State>() {
            @Override
            public boolean test(ImageCleanerTriggerService.State state) {
              return state.executionState == ImageCleanerTriggerService.ExecutionState.RUNNING;
            }
          });

      // Check response
      assertThat(response.executionState, is(ImageCleanerTriggerService.ExecutionState.RUNNING));
    }

    @Test(dataProvider = "hostCount")
    public void testTriggerSuccess(int hostCount) throws Throwable {
      request.executionState = ImageCleanerTriggerService.ExecutionState.RUNNING;
      request.shouldTriggerTasks = true;

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory,
          serviceConfigFactory, nsxClientFactory, hostCount);

      // Send a patch to the trigger service to simulate a maintenance interval kicking in
      machine.sendPatchAndWait(machine.getTriggerCleanerServiceUri(), request);

      // Check that CleanerService was triggered.
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(
              ImageCleanerService.State.class,
              TaskState.TaskStage.STARTED,
              TaskState.TaskStage.FINISHED,
              TaskState.TaskStage.FAILED);
      spec.options.add(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);;

      QueryTask query = QueryTask.create(spec)
          .setDirect(true);
      QueryTask queryResponse = machine.waitForQuery(query,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() >= 1;
            }
          });
      assertThat(queryResponse.results.documentLinks.size(), greaterThanOrEqualTo(1));

      /*
       * Verify that expiration time is set correctly.
       */
      BigDecimal delta = new BigDecimal(TimeUnit.SECONDS.toMicros(10));
      BigDecimal expectedExpiration = new BigDecimal(
          ServiceUtils.computeExpirationTime(
              ImageCleanerTriggerService.DEFAULT_TRIGGER_INTERVAL *
                  ImageCleanerTriggerService.EXPIRATION_TIME_MULTIPLIER));

      for (Object document : queryResponse.results.documents.values()) {
        ServiceDocument doc = Utils.fromJson(document, ServiceDocument.class);
        assertThat(new BigDecimal(doc.documentExpirationTimeMicros), is(closeTo(expectedExpiration, delta)));
      }
    }
  }
}
