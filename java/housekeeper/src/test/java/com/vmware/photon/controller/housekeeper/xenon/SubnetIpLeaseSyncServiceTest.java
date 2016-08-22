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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.cloudstore.xenon.entity.IpLeaseService;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.housekeeper.helpers.xenon.TestEnvironment;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.collect.ImmutableMap;

import org.hamcrest.Matchers;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Tests {@link com.vmware.photon.controller.housekeeper.xenon.SubnetIPLeaseSyncService}.
 */
public class SubnetIpLeaseSyncServiceTest {

  private static final int TEST_PAGE_LIMIT = 100;

  private BasicServiceHost host;
  private SubnetIPLeaseSyncService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new SubnetIPLeaseSyncService();
    }

    /**
     * Test that the service starts with the expected capabilities.
     */
    @Test
    public void testServiceOptions() {
      // Factory capability is implicitly added as part of the factory constructor.
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
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
      service = new SubnetIPLeaseSyncService();
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
      SubnetIPLeaseSyncService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), Matchers.is(200));

      SubnetIPLeaseSyncService.State savedState = host.getServiceState(SubnetIPLeaseSyncService.State.class);
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
     *
     * @throws Throwable
     */
    @Test(dataProvider = "AutoInitializedFields")
    public void testAutoInitializedFields(String fieldName, Object value) throws Throwable {
      SubnetIPLeaseSyncService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), Matchers.is(200));

      SubnetIPLeaseSyncService.State savedState = host.getServiceState(SubnetIPLeaseSyncService.State.class);
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
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      SubnetIPLeaseSyncService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), Matchers.is(200));

      SubnetIPLeaseSyncService.State savedState = host.getServiceState(SubnetIPLeaseSyncService.State.class);
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
    SubnetIPLeaseSyncService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new SubnetIPLeaseSyncService();
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
    private TestEnvironment.Builder machineBuilder;

    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private SubnetIPLeaseSyncService.State request;
    public final Map<Class<? extends Service>, Supplier<FactoryService>> factoryServicesMap =
            ImmutableMap.<Class<? extends Service>, Supplier<FactoryService>>builder()
                    .put(IpLeaseService.class, IpLeaseService::createFactory)
                    .build();

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      cloudStoreHelper = new CloudStoreHelper();

      machineBuilder = new TestEnvironment.Builder()
              .cloudStoreHelper(cloudStoreHelper)
              .hostClientFactory(hostClientFactory);
      // Build input.
      request = buildValidStartupState();
      request.taskState = new TaskState();
      request.taskState.stage = TaskState.TaskStage.CREATED;
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
     * Tests sync success scenarios.
     *
     * @param totalIpLeases
     * @param totalWithNoVMId
     *
     * @throws Throwable
     */
    @Test(dataProvider = "Success")
    public void testSuccess(int totalIpLeases, int totalWithNoVMId)
        throws Throwable {
      machine = machineBuilder
              .hostCount(1)
              .build();

      ServiceHostUtils.startFactoryServices(machine.getHosts()[0], factoryServicesMap);

      seedTestEnvironment(machine, totalIpLeases, totalWithNoVMId);

      SubnetIPLeaseSyncService.State response = machine.callServiceAndWaitForState(
              SubnetIPLeaseSyncService.FACTORY_LINK,
              request,
              SubnetIPLeaseSyncService.State.class,
              (SubnetIPLeaseSyncService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      if (totalIpLeases > 0) {
        assertThat(response.subnetIPLease.subnetId, Matchers.is(response.subnetId));
        assertNotNull(response.subnetIPLease.ipToMACAddressMap);
      }
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() {
      return new Object[][]{
          {0, 0},
          {2, 2},
          {5, 3},
          // Test cases with Ip Lease service documents greater than the default page limit.
          {TEST_PAGE_LIMIT + 10, TEST_PAGE_LIMIT + 1},
      };
    }

    private void seedTestEnvironment(TestEnvironment env, int totalIpLeases, int totalWithNoVMId) throws Throwable {
      for (int i = 0; i < totalIpLeases; i++) {
        IpLeaseService.State state = new IpLeaseService.State();
        state.documentSelfLink = "ip-lease-" + i;
        state.subnetId = "subnet-id";
        state.ip = "dummy-ip";

        if (i >= totalWithNoVMId) {
          state.ownerVmId = "vmId" + i;
        }

        env.sendPostAndWaitForReplication(IpLeaseService.FACTORY_LINK, state);
      }
    }
  }

  private SubnetIPLeaseSyncService.State buildValidStartupState() {
    SubnetIPLeaseSyncService.State state = new SubnetIPLeaseSyncService.State();
    state.isSelfProgressionDisabled = true;
    state.subnetId = "subnet-id";
    return state;
  }
}
