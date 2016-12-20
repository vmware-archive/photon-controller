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

import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Tests {@link TombstoneCleanerService}.
 */
public class TombstoneCleanerServiceTest {

  private BasicServiceHost host;
  private TombstoneCleanerService service;

  private static final long SLEEP_TIME_MILLIS = 1000;
  private static final long MAX_ITERATIONS = 60;

  private static final Logger logger = LoggerFactory.getLogger(TombstoneCleanerServiceTest.class);

  private TombstoneCleanerService.State buildValidStartupState() {
    TombstoneCleanerService.State state = new TombstoneCleanerService.State();
    state.isSelfProgressionDisabled = true;
    state.tombstoneExpirationAgeMillis = 1 * 60 * 60 * 1000L;
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
      service = new TombstoneCleanerService();
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
      service = new TombstoneCleanerService();
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
      TombstoneCleanerService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      TombstoneCleanerService.State savedState = host.getServiceState(TombstoneCleanerService.State.class);
      assertThat(savedState.documentSelfLink, is(BasicServiceHost.SERVICE_URI));
      assertThat(savedState.tombstoneExpirationAgeMillis, is(startState.tombstoneExpirationAgeMillis));

      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(
                  ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.SECONDS.toMicros(10)))));
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @throws Throwable
     */
    @Test(dataProvider = "PositiveFields",
        expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = ".* must be greater than zero")
    public void testPositiveFields(String fieldName, Object value) throws Throwable {
      TombstoneCleanerService.State startState = buildValidStartupState();

      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, value);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "PositiveFields")
    public Object[][] getPositiveFieldsParams() {
      return new Object[][]{
          {"tombstoneExpirationAgeMillis", 0L},
          {"tombstoneExpirationAgeMillis", -1L}
      };
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
      TombstoneCleanerService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      TombstoneCleanerService.State savedState = host.getServiceState(TombstoneCleanerService.State.class);
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
          {"staleTombstones", 0},
          {"staleTasks", 0},
          {"deletedTombstones", 0},
          {"deletedTasks", 0}
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
      TombstoneCleanerService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      TombstoneCleanerService.State savedState = host.getServiceState(TombstoneCleanerService.State.class);
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
    TombstoneCleanerService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new TombstoneCleanerService();
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

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "ImmutableFields",
        expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = ".* is immutable")
    public void testImmutableFields(String fieldName, Object value) throws Throwable {
      TombstoneCleanerService.State patchState = new TombstoneCleanerService.State();
      Field fieldObj = patchState.getClass().getField(fieldName);
      fieldObj.set(patchState, value);

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(op);
    }

    @DataProvider(name = "ImmutableFields")
    public Object[][] getImmutableFieldsParams() {
      return new Object[][]{
          {"tombstoneExpirationAgeMillis", 10L}
      };
    }
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {

    private TestEnvironment machine;
    private TombstoneCleanerService.State request;

    @BeforeMethod
    public void setUp(Object[] testArgs) throws Throwable {
      // Build input.
      request = buildValidStartupState();
      request.isSelfProgressionDisabled = false;
      int totalTombstones = (int) testArgs[0];
      int staleTombstones = (int) testArgs[1];
      int tasksPerTombstone = (int) testArgs[2];
      int hostCount = (int) testArgs[3];

      machine = TestEnvironment.create(hostCount);
      seedTestEnvironment(machine, totalTombstones, staleTombstones, tasksPerTombstone);
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
    public void testSuccess(int totalTombstones, int staleTombstones, int tasksPerTombstone, int hostCount)
        throws Throwable {

      TombstoneCleanerService.State response = machine.callServiceAndWaitForState(
          TombstoneCleanerFactoryService.SELF_LINK,
          request,
          TombstoneCleanerService.State.class,
          (TombstoneCleanerService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      // check final state
      assertThat(response.tombstoneExpirationAgeMillis, is(request.tombstoneExpirationAgeMillis));
      assertThat(response.staleTombstones, is(staleTombstones));
      assertThat(response.staleTasks, is(tasksPerTombstone * staleTombstones));
      assertThat(response.deletedTombstones, is(staleTombstones));
      assertThat(response.deletedTasks, is(tasksPerTombstone * staleTombstones));

      // Check stats.
      ServiceStats stats = machine.getOwnerServiceStats(response);
      assertThat(
          stats.entries.get(Service.Action.PATCH + Service.STAT_NAME_REQUEST_COUNT).latestValue,
          is(2.0)
      );

      //check if we are able to converge to the right number of remaining tombstones
      waitForReplication(machine, totalTombstones - staleTombstones, TombstoneServiceFactory.SELF_LINK);

      //check if we are able to converge to the right number of remaining tasks
      waitForReplication(machine, (totalTombstones - staleTombstones) * tasksPerTombstone,
          TaskServiceFactory.SELF_LINK);
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() {
      return new Object[][]{
          {0, 0, 0, 1},
          {2, 0, 0, 1},
          {2, 0, 0, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
          {2, 0, 5, 1},
          {5, 5, 5, 1},
          {7, 5, 0, 1},
          {7, 5, 5, 1},
          {7, 5, 5, TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }

    private void seedTestEnvironment(TestEnvironment env,
                                     int totalTombstones,
                                     int staleTombstones,
                                     int tasksPerTombstone)
        throws Throwable {
      for (int i = 0; i < totalTombstones; i++) {
        TombstoneService.State tombstone = new TombstoneService.State();
        tombstone.entityId = "entity-id" + i;
        tombstone.entityKind = "entity-kind";
        tombstone.tombstoneTime = System.currentTimeMillis();
        if (i < staleTombstones) {
          tombstone.tombstoneTime -= (request.tombstoneExpirationAgeMillis + 10000);
        }
        env.sendPostAndWait(TombstoneServiceFactory.SELF_LINK, tombstone);

        // create tasks
        for (int j = 0; j < tasksPerTombstone; j++) {
          TaskService.State task = new TaskService.State();
          task.entityId = tombstone.entityId;
          task.entityKind = tombstone.entityKind;
          env.sendPostAndWait(TaskServiceFactory.SELF_LINK, task);
        }
      }

      // Lets wait for all tombstones to be created on all nodes
      waitForReplication(env, totalTombstones, TombstoneServiceFactory.SELF_LINK);
    }

    private void waitForReplication(TestEnvironment env, int count, String serviceLink) throws Throwable {
      for (ServiceHost host : env.getHosts()) {
        ServiceHostUtils.waitForServiceState(
            ServiceDocumentQueryResult.class,
            serviceLink,
            (ServiceDocumentQueryResult result) -> {
              logger.info(
                  "Host:[{}] Service:[{}] Document Count- Expected [{}], Actual [{}]",
                  host.getUri(),
                  serviceLink,
                  count,
                  result.documentCount);
              return result.documentCount == count;
            },
            host, SLEEP_TIME_MILLIS, MAX_ITERATIONS,
            null);
      }
    }
  }
}
