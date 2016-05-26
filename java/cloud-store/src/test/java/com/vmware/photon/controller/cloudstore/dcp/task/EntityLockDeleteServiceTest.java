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

package com.vmware.photon.controller.cloudstore.dcp.task;

import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockService;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.VmService;
import com.vmware.photon.controller.cloudstore.dcp.entity.VmServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.dcp.task.EntityLockDeleteService}.
 */
public class EntityLockDeleteServiceTest {

  private BasicServiceHost host;
  private EntityLockDeleteService service;

  private EntityLockDeleteService.State buildValidStartupState() {
    EntityLockDeleteService.State state = new EntityLockDeleteService.State();
    state.isSelfProgressionDisabled = true;
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
      service = new EntityLockDeleteService();
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
      service = new EntityLockDeleteService();
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
      EntityLockDeleteService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      EntityLockDeleteService.State savedState = host.getServiceState(EntityLockDeleteService.State.class);
      assertThat(savedState.documentSelfLink, is(BasicServiceHost.SERVICE_URI));
      assertEquals(savedState.entityLockDeleteWatermarkTimeInMicros,
          (Long) EntityLockDeleteService.DEFAULT_DELETE_WATERMARK_TIME_MILLIS);
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
      EntityLockDeleteService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      EntityLockDeleteService.State savedState = host.getServiceState(EntityLockDeleteService.State.class);
      if (fieldObj.getType().equals(TaskState.class)) {
        assertThat(Utils.toJson(fieldObj.get(savedState)), is(Utils.toJson(value)));
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
          {"danglingEntityLocksWithDeletedEntities", 0},
          {"deletedEntityLocks", 0}
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
      EntityLockDeleteService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      EntityLockDeleteService.State savedState = host.getServiceState(EntityLockDeleteService.State.class);
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
    EntityLockDeleteService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new EntityLockDeleteService();
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
    private EntityLockDeleteService.State request;
    private List<String> testSelfLinks = new ArrayList<>();

    @BeforeMethod
    public void setUp() throws Throwable {
      // Build input.
      request = buildValidStartupState();
      request.isSelfProgressionDisabled = false;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        freeTestEnvironment(machine);
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
    public void testSuccessOnNewEntityLocks(int totalEntityLocks, int danglingEntityLocks, int hostCount)
        throws Throwable {
      machine = TestEnvironment.create(hostCount);
      seedTestEnvironment(machine, totalEntityLocks, danglingEntityLocks);

      // No entity locks should be deleted when entityLockDeleteWatermarkTimeInMicros is NowMicrosUtc
      request.entityLockDeleteWatermarkTimeInMicros = Utils.getNowMicrosUtc();
      EntityLockDeleteService.State response = machine.callServiceAndWaitForState(
          EntityLockDeleteFactoryService.SELF_LINK,
          request,
          EntityLockDeleteService.State.class,
          (EntityLockDeleteService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      assertThat(response.danglingEntityLocksWithDeletedEntities, is(0));
      assertThat(response.deletedEntityLocks, is(0));
    }

    /**
     * Tests clean success scenarios.
     *
     * @param hostCount
     * @throws Throwable
     */
    @Test(dataProvider = "Success")
    public void testSuccessOnOldEntityLocks(int totalEntityLocks, int danglingEntityLocks, int hostCount)
        throws Throwable {
      machine = TestEnvironment.create(hostCount);
      seedTestEnvironment(machine, totalEntityLocks, danglingEntityLocks);

      // All entity locks being created should be found when entityLockDeleteWatermarkTimeInMicros is 0
      request.entityLockDeleteWatermarkTimeInMicros = 0L;
      EntityLockDeleteService.State response = machine.callServiceAndWaitForState(
          EntityLockDeleteFactoryService.SELF_LINK,
          request,
          EntityLockDeleteService.State.class,
          (EntityLockDeleteService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);
      assertThat(response.danglingEntityLocksWithDeletedEntities,
          is(danglingEntityLocks));
      assertThat(response.deletedEntityLocks,
          is(danglingEntityLocks));

      verifyLockStatusAfterCleanup(machine, totalEntityLocks, danglingEntityLocks);
    }

    private void freeTestEnvironment(TestEnvironment machine) throws Throwable {
      try {
        for (String selfLink : testSelfLinks) {
          machine.deleteService(selfLink);
        }
      } finally {
        testSelfLinks.clear();
      }
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() {
      return new Object[][]{
          {0, 0, 1},
          {2, 0, 1},
          {2, 0, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
          {5, 5, 1},
          {7, 5, 1},
          {7, 5, TestEnvironment.DEFAULT_MULTI_HOST_COUNT},
          // Test cases with entity locks greater than the default page limit.
          {EntityLockDeleteService.DEFAULT_PAGE_LIMIT + 100, EntityLockDeleteService
              .DEFAULT_PAGE_LIMIT + 100, 1},
          {EntityLockDeleteService.DEFAULT_PAGE_LIMIT + 100, 1, 1},
      };
    }

    private void seedTestEnvironment(TestEnvironment env,
                                     int totalEntityLocks,
                                     int danglingEntityLocks) throws Throwable {
      for (int i = 0; i < totalEntityLocks; i++) {

        // Create associated entity lock without creating task, but only with fake task id(that acts as deleted task)
        EntityLockService.State entityLock = new EntityLockService.State();
        entityLock.entityId = "entity-id" + i;
        entityLock.ownerTaskId = "task-id" + i;
        entityLock.entityKind = Vm.KIND;
        entityLock.lockOperation = EntityLockService.State.LockOperation.ACQUIRE;
        entityLock.documentSelfLink = EntityLockServiceFactory.SELF_LINK + "/" + entityLock.entityId;
        Operation entityLockOperation = env.sendPostAndWait(EntityLockServiceFactory.SELF_LINK, entityLock);
        EntityLockService.State createdEntityLock = entityLockOperation.getBody(EntityLockService.State.class);

        entityLock.entityId = createdEntityLock.entityId;
        entityLock.ownerTaskId = createdEntityLock.ownerTaskId;
        entityLock.entityKind = Vm.KIND;
        entityLock.lockOperation = EntityLockService.State.LockOperation.RELEASE;
        entityLock.documentSelfLink = EntityLockServiceFactory.SELF_LINK + "/" + entityLock.entityId;
        env.sendPostAndWait(EntityLockServiceFactory.SELF_LINK, entityLock);

        if (i < (totalEntityLocks - danglingEntityLocks)) {
          VmService.State vm = new VmService.State();
          vm.name = UUID.randomUUID().toString();
          vm.flavorId = UUID.randomUUID().toString();
          vm.projectId = UUID.randomUUID().toString();
          vm.imageId = UUID.randomUUID().toString();
          vm.vmState = VmState.CREATING;
          vm.documentSelfLink = createdEntityLock.entitySelfLink;
          Operation vmCreate = env.sendPostAndWait(VmServiceFactory.SELF_LINK, vm);
          VmService.State vmState = vmCreate.getBody(VmService.State.class);

          testSelfLinks.add(createdEntityLock.documentSelfLink);
          testSelfLinks.add(vmState.documentSelfLink);
        }
      }
    }

    private void verifyLockStatusAfterCleanup(TestEnvironment env,
                                              int totalEntityLocks,
                                              int danglingEntityLocks) throws Throwable {

      QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(EntityLockService.State.class));

      querySpec.query
          .addBooleanClause(kindClause);

      QueryTask queryTask = QueryTask.create(querySpec).setDirect(true);
      NodeGroupBroadcastResponse response = env.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils
              .getBroadcastQueryDocumentLinks(response).size(),
          is(totalEntityLocks - danglingEntityLocks));
    }
  }
}
