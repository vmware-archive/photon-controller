/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.cloudstore.xenon.task;

import com.vmware.photon.controller.api.model.VmState;
import com.vmware.photon.controller.cloudstore.xenon.entity.IpLeaseService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.hamcrest.Matchers;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.xenon.task.IpLeaseCleanerService}.
 */
public class IpLeaseCleanerServiceTest {

  private static final int TEST_PAGE_LIMIT = 100;

  private BasicServiceHost host;
  private IpLeaseCleanerService service;

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
    public void setUp() throws Throwable {
      service = new IpLeaseCleanerService();
    }

    /**
     * Test that the service starts with the expected capabilities.
     */
    @Test
    public void testServiceOptions() {
      // Factory capability is implicitly added as part of the factory constructor.
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), Matchers.is(expected));
    }
  }


  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new IpLeaseCleanerService();
      host = BasicServiceHost.create();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test start of service.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      IpLeaseCleanerService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), Matchers.is(200));

      IpLeaseCleanerService.State savedState = host.getServiceState(IpLeaseCleanerService.State.class);
      assertThat(savedState.documentSelfLink, Matchers.is(BasicServiceHost.SERVICE_URI));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          Matchers.is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
                  ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.SECONDS.toMicros(10)))));
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "AutoInitializedFields")
    public void testAutoInitializedFields(String fieldName, Object value) throws Throwable {
      IpLeaseCleanerService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), Matchers.is(200));

      IpLeaseCleanerService.State savedState = host.getServiceState(IpLeaseCleanerService.State.class);
      if (fieldObj.getType().equals(TaskState.class)) {
        assertThat(Utils.toJson(false, false, fieldObj.get(savedState)),
            Matchers.is(Utils.toJson(false, false, value)));
      } else {
        assertThat(fieldObj.get(savedState), Matchers.is(value));
      }
    }

    @DataProvider(name = "AutoInitializedFields")
    public Object[][] getAutoInitializedFieldsParams() {
      TaskState state = new TaskState();
      state.stage = TaskState.TaskStage.STARTED;

      return new Object[][]{
          {"taskState", state},
          {"isSelfProgressionDisabled", false},
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
      IpLeaseCleanerService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), Matchers.is(200));

      IpLeaseCleanerService.State savedState = host.getServiceState(IpLeaseCleanerService.State.class);
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros), Matchers.is(closeTo(expectedTime, delta)));
    }

    @DataProvider(name = "ExpirationTime")
    public Object[][] getExpirationTime() {
      long expTime = ServiceUtils.computeExpirationTime(TimeUnit.HOURS.toMillis(1));

      return new Object[][]{
          {
              -10L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.SECONDS.toMicros(10))
          },
          {
              0L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.SECONDS.toMicros(10))
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
    IpLeaseCleanerService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new IpLeaseCleanerService();
      serviceState = buildValidStartupState();
      host.startServiceSynchronously(service, serviceState);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation with invalid payload.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatch() throws Throwable {
      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody("invalid body");

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
    private IpLeaseCleanerService.State request;

    @BeforeMethod
    public void setUp() throws Throwable {
      // Build input.
      request = buildValidStartupState();
      request.isSelfProgressionDisabled = false;
      request.pageLimit = TEST_PAGE_LIMIT;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        // Note that this will fully clean up the Xenon host's Lucene index: all
        // services we created will be fully removed.
        machine.stop();
        machine = null;
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
    public void testSuccess(int totalIpLeases, int danglingIpLeases, int hostCount)
        throws Throwable {
      machine = TestEnvironment.create(hostCount);
      seedTestEnvironment(machine, totalIpLeases, danglingIpLeases);
      request.isSelfProgressionDisabled = false;

      IpLeaseCleanerService.State response = machine.callServiceAndWaitForState(
          IpLeaseCleanerService.FACTORY_LINK,
          request,
          IpLeaseCleanerService.State.class,
          (IpLeaseCleanerService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      // Check that ownerVmId from IpLease document was cleaned.
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(com.vmware.xenon.common.Utils.buildKind(IpLeaseService.State.class));
      QueryTask.Query ownerVmIdClause = new QueryTask.Query()
          .setTermPropertyName("ownerVmId")
          .setTermMatchValue("vm-id");

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(ownerVmIdClause);

      QueryTask query = QueryTask.create(spec)
          .setDirect(true);
      QueryTask queryResponse = machine.waitForQuery(query,
          (QueryTask queryTask) ->
              queryTask.results.documentLinks.size() == totalIpLeases - danglingIpLeases
      );
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() {
      return new Object[][]{
          {0, 0, 1},
          {0, 0, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
          {2, 2, 1},
          {2, 2, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
          {5, 3, 1},
          {5, 3, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
          // Test cases with Ip Lease service documents greater than the default page limit.
          {TEST_PAGE_LIMIT + 10, TEST_PAGE_LIMIT + 1, 1},
          {TEST_PAGE_LIMIT + 10, TEST_PAGE_LIMIT + 1, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
      };
    }

    private void seedTestEnvironment(TestEnvironment env,
                                     int totalIpLeases, int danglingIpLeases) throws Throwable {

      VmService.State vmState = new VmService.State();
      vmState.name = "vm-name";
      vmState.flavorId = "flavor-id";
      vmState.imageId = "image-id";
      vmState.documentSelfLink = "vm-id";
      vmState.projectId = "project-id";
      vmState.vmState = VmState.STARTED;

      env.sendPostAndWait(VmServiceFactory.SELF_LINK, vmState);

      for (int i = 0; i < totalIpLeases; i++) {
        IpLeaseService.State state = new IpLeaseService.State();
        state.documentSelfLink = "ip-lease-" + i;
        state.subnetId = "subnet-id";
        state.ip = "dummy-ip";
        state.ownerVmId = "non-existing-vm-id";

        if (i >= danglingIpLeases) {
          state.ownerVmId = "vm-id";
        }
        env.sendPostAndWaitForReplication(
            IpLeaseService.FACTORY_LINK, state);
      }
    }
  }

  private IpLeaseCleanerService.State buildValidStartupState() {
    IpLeaseCleanerService.State state = new IpLeaseCleanerService.State();
    state.isSelfProgressionDisabled = true;
    return state;
  }
}
