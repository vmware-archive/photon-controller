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
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.dcp.mock.HostClientMock;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Tests {@link com.vmware.photon.controller.housekeeper.dcp.ImageSeederSyncTriggerService}.
 */
public class ImageSeederSyncTriggerServiceTest {

  private TestHost host;
  private ImageSeederSyncTriggerService service;

  private ImageSeederSyncTriggerService.State buildValidStartupState() {
    ImageSeederSyncTriggerService.State state = new ImageSeederSyncTriggerService.State();

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
      service = new ImageSeederSyncTriggerService();
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
      service = spy(new ImageSeederSyncTriggerService());
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

      ImageSeederSyncTriggerService.State savedState = host.getServiceState(ImageSeederSyncTriggerService.State.class);
      assertThat(savedState.triggersError, is(new Long(0)));
      assertThat(savedState.triggersSuccess, is(new Long(0)));
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageSeederSyncTriggerService());
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
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {
    private TestEnvironment machine;
    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ZookeeperHostMonitor zookeeperHostMonitor;
    private ServiceConfigFactory serviceConfigFactory;

    private ImageSeederSyncTriggerService.State request;

    @BeforeMethod
    public void setup() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      cloudStoreHelper = mock(CloudStoreHelper.class);
      doReturn(new HostClientMock()).when(hostClientFactory).create();
      zookeeperHostMonitor = mock(ZookeeperHostMonitor.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);

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
    public void testTriggerSuccess(int hostCount) throws Throwable {
      request.pulse = true;

      machine = TestEnvironment.create(cloudStoreHelper, hostClientFactory, zookeeperHostMonitor,
          serviceConfigFactory, hostCount);

      // Send a patch to the trigger service to simulate a maintenance interval kicking in
      machine.sendPatchAndWait(machine.getTriggerCleanerServiceUri(), request);

      // Check that CleanerService was triggered.
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(
              ImageCleanerService.State.class,
              TaskState.TaskStage.STARTED,
              TaskState.TaskStage.FINISHED,
              TaskState.TaskStage.FAILED);

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
    }
  }
}
