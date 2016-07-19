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

package com.vmware.photon.controller.cloudstore.xenon.task;

import com.vmware.photon.controller.api.model.AvailabilityZoneState;
import com.vmware.photon.controller.cloudstore.xenon.entity.AvailabilityZoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.xenon.task.AvailabilityZoneCleanerService}.
 */
public class AvailabilityZoneCleanerServiceTest {

  private BasicServiceHost host;
  private AvailabilityZoneCleanerService service;

  private AvailabilityZoneCleanerService.State buildValidStartupState() {
    AvailabilityZoneCleanerService.State state = new AvailabilityZoneCleanerService.State();
    state.isSelfProgressionDisabled = true;
    state.availabilityZoneExpirationAgeInMicros =
        AvailabilityZoneCleanerService.DEFAULT_AVAILABILITY_ZONE_EXPIRATION_AGE_IN_MICROS;
    return state;
  }

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
      service = new AvailabilityZoneCleanerService();
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
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new AvailabilityZoneCleanerService();
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
      AvailabilityZoneCleanerService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      AvailabilityZoneCleanerService.State savedState =
          host.getServiceState(AvailabilityZoneCleanerService.State.class);
      assertThat(savedState.documentSelfLink, is(BasicServiceHost.SERVICE_URI));
      assertThat(savedState.availabilityZoneExpirationAgeInMicros,
              is(startState.availabilityZoneExpirationAgeInMicros));

      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
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
      AvailabilityZoneCleanerService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      AvailabilityZoneCleanerService.State savedState =
                     host.getServiceState(AvailabilityZoneCleanerService.State.class);
      if (fieldObj.getType().equals(TaskState.class)) {
        assertThat(Utils.toJson(false, false, fieldObj.get(savedState)), is(Utils.toJson(false, false, value)));
      } else {
        assertThat(fieldObj.get(savedState), is(value));
      }
    }

    @DataProvider(name = "AutoInitializedFields")
    public Object[][] getAutoInitializedFieldsParams() {
      TaskState state = new TaskState();
      state.stage = TaskState.TaskStage.STARTED;

      return new Object[][]{
          {"taskState", state},
          {"isSelfProgressionDisabled", false},
          {"staleAvailabilityZones", 0},
          {"deletedAvailabilityZones", 0},
          {"availabilityZoneExpirationAgeInMicros",
              AvailabilityZoneCleanerService.DEFAULT_AVAILABILITY_ZONE_EXPIRATION_AGE_IN_MICROS},
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
      AvailabilityZoneCleanerService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      AvailabilityZoneCleanerService.State savedState =
                                          host.getServiceState(AvailabilityZoneCleanerService.State.class);
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros), is(closeTo(expectedTime, delta)));
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
    AvailabilityZoneCleanerService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new AvailabilityZoneCleanerService();
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
    private AvailabilityZoneCleanerService.State request;

    @BeforeMethod
    public void setUp() throws Throwable {
      // Build input.
      request = buildValidStartupState();
      request.isSelfProgressionDisabled = false;
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
     * Tests clean success scenarios.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "Success")
    public void testSuccess(
        int totalAvailabilityZones,
        int staleAvailabilityZones,
        int staleAvailabilityZonesAssociatedWithHosts,
        int hostCount)
        throws Throwable {

      machine = TestEnvironment.create(hostCount);
      seedTestEnvironment(
          machine,
          totalAvailabilityZones,
          staleAvailabilityZones,
          staleAvailabilityZonesAssociatedWithHosts);

      // All availability zones being created should be found when availabilityZoneExpirationAgeInMicros is 0
      request.availabilityZoneExpirationAgeInMicros = 0L;
      AvailabilityZoneCleanerService.State response = machine.callServiceAndWaitForState(
          AvailabilityZoneCleanerFactoryService.SELF_LINK,
          request,
          AvailabilityZoneCleanerService.State.class,
          (AvailabilityZoneCleanerService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      // check final state
      int expectedAvailabilityZonesDeleted = staleAvailabilityZones - staleAvailabilityZonesAssociatedWithHosts;
      assertThat(response.availabilityZoneExpirationAgeInMicros, is(request.availabilityZoneExpirationAgeInMicros));
      assertThat(response.staleAvailabilityZones, is(staleAvailabilityZones));
      assertThat(response.deletedAvailabilityZones, is(expectedAvailabilityZonesDeleted));
    }

    /**
     * Tests clean success scenarios.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "Success")
    public void testSuccessOnNewAvailabilityZones(
        int totalAvailabilityZones,
        int staleAvailabilityZones,
        int staleAvailabilityZonesAssociatedWithHosts,
        int hostCount)
        throws Throwable {

      machine = TestEnvironment.create(hostCount);
      seedTestEnvironment(
          machine,
          totalAvailabilityZones,
          staleAvailabilityZones,
          staleAvailabilityZonesAssociatedWithHosts);

      // Availability zones being created should not be found when availabilityZoneExpirationAgeInMicros is NowMicrosUtc
      request.availabilityZoneExpirationAgeInMicros = Utils.getNowMicrosUtc();
      AvailabilityZoneCleanerService.State response = machine.callServiceAndWaitForState(
          AvailabilityZoneCleanerFactoryService.SELF_LINK,
          request,
          AvailabilityZoneCleanerService.State.class,
          (AvailabilityZoneCleanerService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      // check final state
      assertThat(response.availabilityZoneExpirationAgeInMicros, is(request.availabilityZoneExpirationAgeInMicros));
      assertThat(response.staleAvailabilityZones, is(0));
      assertThat(response.deletedAvailabilityZones, is(0));
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() {
      return new Object[][]{
          {0, 0, 0, 1},
          {2, 2, 0, 1},
          {2, 2, 0, 1},
          {2, 2, 2, 1},
          {2, 2, 2, 1},
          {2, 0, 0, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
          {7, 5, 2, 1},
          {9, 7, 2, 1},
          {9, 7, 2, TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }

    private void seedTestEnvironment(TestEnvironment env,
                                     int totalAvailabilityZones,
                                     int staleAvailabilityZones,
                                     int staleAvailabilityZonesAssociatedWithHosts)
        throws Throwable {
      for (int i = 0; i < totalAvailabilityZones; i++) {
        // create availability zone
        AvailabilityZoneService.State availabilityZone = new AvailabilityZoneService.State();
        availabilityZone.name = "availability-zone" + i;
        availabilityZone.state = AvailabilityZoneState.READY;
        if (i < staleAvailabilityZones) {
          availabilityZone.state = AvailabilityZoneState.PENDING_DELETE;
        }
        Operation availabilityZoneOperation =
            env.sendPostAndWaitForReplication(AvailabilityZoneServiceFactory.SELF_LINK, availabilityZone);
        AvailabilityZoneService.State createdAvailabilityZone =
            availabilityZoneOperation.getBody(AvailabilityZoneService.State.class);

        // create Host
        if (i < staleAvailabilityZonesAssociatedWithHosts) {
          HostService.State host = TestHelper.getHostServiceStartState();
          host.availabilityZoneId = ServiceUtils.getIDFromDocumentSelfLink(createdAvailabilityZone.documentSelfLink);
          Operation hostOperation = env.sendPostAndWait(HostServiceFactory.SELF_LINK, host);
          HostService.State createdHost = hostOperation.getBody(HostService.State.class);
        }
      }
    }
  }
}
