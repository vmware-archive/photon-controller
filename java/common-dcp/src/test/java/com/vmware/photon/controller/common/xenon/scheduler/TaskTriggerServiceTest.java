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

package com.vmware.photon.controller.common.xenon.scheduler;

import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStage;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStageFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceConfiguration;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link TaskTriggerService}.
 */
public class TaskTriggerServiceTest {

  private BasicServiceHost host;
  private TaskTriggerService service;
  private String selfLink = TaskTriggerFactoryService.SELF_LINK + "/test-service-scheduler";

  private TaskTriggerService.State buildValidStartupState() {
    TestServiceWithStage.State nestedState = new TestServiceWithStage.State();
    nestedState.taskInfo = new TaskState();
    nestedState.taskInfo.stage = TaskState.TaskStage.STARTED;

    TaskTriggerService.State state = new TaskTriggerService.State();
    state.factoryServiceLink = TestServiceWithStageFactory.SELF_LINK;
    state.serializedTriggerState = Utils.toJson(nestedState);
    state.triggerStateClassName = nestedState.getClass().getTypeName();
    state.taskExpirationAgeMillis = 5 * 60 * 60 * 1000;

    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = true)
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new TaskTriggerService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE,
          Service.ServiceOption.PERSISTENCE);
      assertThat(service.getOptions(), is(expected));
      assertThat(service.getMaintenanceIntervalMicros(),
          is(TimeUnit.MILLISECONDS.toMicros(TaskTriggerService.DEFAULT_MAINTENANCE_INTERVAL_MILLIS)));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TaskTriggerService();
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
      TaskTriggerService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState, selfLink);
      assertThat(startOp.getStatusCode(), is(200));

      TaskTriggerService.State savedState = host.getServiceState(TaskTriggerService.State.class, selfLink);
      assertThat(savedState.documentSelfLink, is(selfLink));
      assertThat(Utils.toJson(savedState.serializedTriggerState), is(Utils.toJson(startState.serializedTriggerState)));
      assertThat(savedState.factoryServiceLink, is(startState.factoryServiceLink));
      assertThat(savedState.taskExpirationAgeMillis, is(startState.taskExpirationAgeMillis));

      ServiceConfiguration config = host.getServiceState(ServiceConfiguration.class, selfLink + "/config");
      assertThat(config.maintenanceIntervalMicros,
          is(TimeUnit.MILLISECONDS.toMicros(TaskTriggerService.DEFAULT_MAINTENANCE_INTERVAL_MILLIS)));
    }

    /**
     * Test that maintenance interval passed in the start state is applied.
     *
     * @throws Throwable
     */
    @Test
    public void testCustomMaintenanceInerval() throws Throwable {
      TaskTriggerService.State startState = buildValidStartupState();
      startState.triggerIntervalMillis = 25 * 60 * 1000;

      Operation startOp = host.startServiceSynchronously(service, startState, selfLink);
      assertThat(startOp.getStatusCode(), is(200));

      ServiceConfiguration config = host.getServiceState(ServiceConfiguration.class, selfLink + "/config");
      assertThat(config.maintenanceIntervalMicros,
          is(TimeUnit.MILLISECONDS.toMicros(startState.triggerIntervalMillis)));
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "NotBlankFields",
        expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "(.* cannot be null|.* cannot be blank)")
    public void testNotBlankFields(String fieldName, Object value) throws Throwable {
      TaskTriggerService.State startState = buildValidStartupState();

      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, value);

      host.startServiceSynchronously(service, startState, selfLink);
    }

    @DataProvider(name = "NotBlankFields")
    public Object[][] getNotBlankFieldsParams() {
      return new Object[][]{
          {"serializedTriggerState", null},
          {"serializedTriggerState", ""},
          {"triggerStateClassName", null},
          {"triggerStateClassName", ""},
          {"factoryServiceLink", null},
          {"factoryServiceLink", ""}
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
      TaskTriggerService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState, selfLink);
      assertThat(startOp.getStatusCode(), is(200));

      TaskTriggerService.State savedState = host.getServiceState(TaskTriggerService.State.class, selfLink);
      assertThat(fieldObj.get(savedState), is(value));
    }

    @DataProvider(name = "AutoInitializedFields")
    public Object[][] getAutoInitializedFieldsParams() {
      return new Object[][]{
          {"taskExpirationAgeMillis", TaskTriggerService.DEFAULT_TASK_EXPIRATION_AGE_MILLIS}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    TaskTriggerService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();
      host.startFactoryServiceSynchronously(new TestServiceWithStageFactory(), TestServiceWithStageFactory.SELF_LINK);

      service = new TaskTriggerService();
      serviceState = buildValidStartupState();
      host.startServiceSynchronously(service, serviceState, selfLink);
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
          .createPatch(UriUtils.buildUri(host, selfLink, null))
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
    @Test(dataProvider = "NotBlankFields",
        expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = ".* cannot be blank")
    public void testNotBlankFields(String fieldName, Object value) throws Throwable {
      TaskTriggerService.State patchState = new TaskTriggerService.State();
      Field fieldObj = patchState.getClass().getField(fieldName);
      fieldObj.set(patchState, value);

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody(patchState);
      host.sendRequestAndWait(op);
    }

    @DataProvider(name = "NotBlankFields")
    public Object[][] getNotBlankFieldsParams() {
      return new Object[][]{
          {"serializedTriggerState", ""},
          {"triggerStateClassName", ""},
          {"factoryServiceLink", ""}
      };
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "PositiveFields",
        expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = ".* must be greater than zero")
    public void testPositiveFields(String fieldName, Object value) throws Throwable {
      TaskTriggerService.State patchState = new TaskTriggerService.State();
      Field fieldObj = patchState.getClass().getField(fieldName);
      fieldObj.set(patchState, value);

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody(patchState);
      host.sendRequestAndWait(op);
    }

    @DataProvider(name = "PositiveFields")
    public Object[][] getPositiveFieldsParams() {
      return new Object[][]{
          {"taskExpirationAgeMillis", 0},
          {"taskExpirationAgeMillis", -10},
          {"triggerIntervalMillis", 0},
          {"triggerIntervalMillis", -10}
      };
    }

    /**
     * Test that maintenance interval passed in the patch is stored and applied.
     *
     * @throws Throwable
     */
    @Test
    public void testUpdateMaintenanceInerval() throws Throwable {
      TaskTriggerService.State patchState = new TaskTriggerService.State();
      patchState.triggerIntervalMillis = 15 * 60 * 1000;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody(patchState);
      Operation result = host.sendRequestAndWait(patch);

      TaskTriggerService.State newServiceState = result.getBody(TaskTriggerService.State.class);
      assertThat(newServiceState.triggerIntervalMillis, is(patchState.triggerIntervalMillis));

      ServiceConfiguration config = host.getServiceState(ServiceConfiguration.class, selfLink + "/config");
      assertThat(config.maintenanceIntervalMicros,
          is(TimeUnit.MILLISECONDS.toMicros(patchState.triggerIntervalMillis)));
    }

    /**
     * Test patch that will trigger a service instance. The patch sent in this test should be an "empty" patch
     * to test that the patch sent by handle maintenance will work correctly.
     *
     * @throws Throwable
     */
    @Test
    public void testTriggerPatch() throws Throwable {
      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody(new TaskSchedulerService.State());
      Operation result = host.sendRequestAndWait(op);

      assertThat(result.getStatusCode(), is(200));

      ServiceDocumentQueryResult doc = host.waitForState(
          serviceState.factoryServiceLink,
          ServiceDocumentQueryResult.class,
          (ServiceDocumentQueryResult o) -> o.documentCount >= 1);
      assertThat(doc.documentCount, is(1L));

      TestServiceWithStage.State triggeredService = host.getServiceState(
          TestServiceWithStage.State.class, doc.documentLinks.get(0));
      assertThat(triggeredService.taskInfo.stage, is(TaskState.TaskStage.STARTED));
    }
  }
}
